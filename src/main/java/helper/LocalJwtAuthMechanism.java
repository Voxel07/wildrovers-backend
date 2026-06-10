package helper;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Handles locally-signed JWTs (username/password login) before OIDC gets involved.
 * Manually creates a JWTParser so SmallRye JWT's built-in auth mechanism
 * (which conflicts with OIDC) is never registered.
 */
@ApplicationScoped
public class LocalJwtAuthMechanism implements HttpAuthenticationMechanism {

    private static final Logger log = Logger.getLogger(LocalJwtAuthMechanism.class.getName());

    private volatile JWTParser jwtParser;
    private final String publicKeyPath = System.getenv().getOrDefault("JWT_PUBLIC_KEY_PATH", "publicKey.pem");

    @Override
    public int getPriority() {
        // IMPORTANT: In Quarkus HttpAuthenticationMechanism, HIGHER value = HIGHER priority = runs first.
        // Quarkus OIDC (service mode) runs at priority ~2001.
        // We must be > 2001 so we intercept locally-signed JWTs BEFORE OIDC tries to validate
        // them against the Authentik server (which would fail and emit a 401 challenge).
        // For OIDC tokens (Authentik-issued), isWildroversToken() returns false → we return
        // nullItem() → OIDC picks up and validates them as normal.
        return 2500;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager ipm) {
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Uni.createFrom().nullItem();
        }

        String token = authHeader.substring(7).trim();

        // Only handle locally-signed tokens — pass everything else to OIDC
        if (!isWildroversToken(token)) {
            return Uni.createFrom().nullItem();
        }

        try {
            JsonWebToken jwt = getParser().parse(token);
            String username = jwt.getName();
            Set<String> groups = jwt.getGroups();
            if (groups == null) groups = Set.of();
            String email = jwt.getClaim("email"); // embedded at login time by JWT.generator()

            var identityBuilder = QuarkusSecurityIdentity.builder()
                .setPrincipal((Principal) () -> username)
                .addRoles(groups)
                .addCredential(new TokenCredential(token, "Bearer"))
                // Mark this identity as locally-signed so UserPrincipalResolver can
                // avoid invoking the OIDC JsonWebToken producer (which logs a WARNING
                // when called outside of an OIDC request context).
                .addAttribute("local-jwt", Boolean.TRUE);
            if (email != null) {
                identityBuilder.addAttribute("email", email);
            }

            log.info("LocalJwtAuth: authenticated '" + username + "' via local JWT");
            return Uni.createFrom().item(identityBuilder.build());
        } catch (Exception e) {
            log.warning("LocalJwtAuth: validation failed — " + e.getMessage());
            return Uni.createFrom().nullItem();
        }
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(TokenAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(
            new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "Bearer"));
    }

    private JWTParser getParser() throws Exception {
        if (jwtParser == null) {
            synchronized (this) {
                if (jwtParser == null) {
                    jwtParser = createParser();
                }
            }
        }
        return jwtParser;
    }

    private JWTParser createParser() throws Exception {
        // Load the public key: classpath first (dev/packaged jar), filesystem fallback
        // (production deployments that set JWT_PUBLIC_KEY_PATH to an absolute path).
        String pem;
        java.io.InputStream classpathStream = getClass().getClassLoader().getResourceAsStream(publicKeyPath);
        if (classpathStream != null) {
            try (classpathStream) {
                pem = new String(classpathStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            log.info("LocalJwtAuth: loaded public key from classpath: " + publicKeyPath);
        } else {
            pem = Files.readString(Path.of(publicKeyPath));
            log.info("LocalJwtAuth: loaded public key from filesystem: " + publicKeyPath);
        }

        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey key = kf.generatePublic(spec);

        JWTAuthContextInfo info = new JWTAuthContextInfo(key, "wildrovers");
        info.setSignatureAlgorithm(Set.of(SignatureAlgorithm.RS256));
        return new DefaultJWTParser(info);
    }

    private boolean isWildroversToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                String json = new String(decoded);
                return json.contains("\"iss\":\"wildrovers\"") ||
                       json.contains("\"iss\" : \"wildrovers\"");
            }
        } catch (Exception ignored) {}
        return false;
    }
}
