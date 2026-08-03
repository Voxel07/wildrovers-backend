package helper;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import model.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import orm.UserOrm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class LocalJwtIdentityService {
    private static final Logger log = Logger.getLogger(LocalJwtIdentityService.class.getName());

    private volatile JWTParser jwtParser;

    @ConfigProperty(name = "local.jwt.public-key.location")
    String publicKeyPath;

    @Inject
    UserOrm userOrm;

    @ActivateRequestContext
    public SecurityIdentity authenticate(String token) {
        try {
            JsonWebToken jwt = getParser().parse(token);
            String username = jwt.getName();
            User user = userOrm.findByUsername(username);
            if (user == null || !user.isActive() || user.getIsBlocked() || user.getRole() == null) {
                log.warning("LocalJwtAuth: rejecting disabled or unknown user '" + username + "'");
                throw new AuthenticationFailedException();
            }

            QuarkusSecurityIdentity.Builder identityBuilder = QuarkusSecurityIdentity.builder()
                    .setPrincipal(jwt)
                    .addRoles(Set.of(user.getRole()))
                    .addCredential(new TokenCredential(token, "Bearer"))
                    .addAttribute("local-jwt", Boolean.TRUE);
            String email = jwt.getClaim("email");
            if (email != null) {
                identityBuilder.addAttribute("email", email);
            }

            log.info("LocalJwtAuth: authenticated '" + username + "' via local JWT");
            return identityBuilder.build();
        } catch (AuthenticationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.log(Level.WARNING, "LocalJwtAuth: validation failed", e);
            throw new AuthenticationFailedException(e);
        }
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
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));

        JWTAuthContextInfo info = new JWTAuthContextInfo(key, "wildrovers");
        info.setSignatureAlgorithm(Set.of(SignatureAlgorithm.RS256));
        info.setExpectedAudience(Set.of("wildrovers-backend"));
        info.setRequiredClaims(Set.of("exp", "iat", "upn"));
        info.setMaxTimeToLiveSecs(12L * 60L * 60L);
        return new DefaultJWTParser(info);
    }
}
