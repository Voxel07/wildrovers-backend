package tools;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Server-side half of the Authentik login (backend for frontend). The browser
 * runs the authorization request with PKCE and hands the code and verifier to
 * the backend, which exchanges and validates the tokens here. Tokens from the
 * identity provider never reach the browser.
 */
@ApplicationScoped
public class OidcClient {
    private static final Logger log = Logger.getLogger(OidcClient.class.getName());

    /** The identity provider rejected the grant (expired, revoked, wrong verifier). */
    public static class GrantRejectedException extends Exception {
        public GrantRejectedException(String message) { super(message); }
    }

    /** Validated claims of a token response plus the tokens the backend keeps. */
    public record Tokens(String issuer, String subject, String preferredUsername, String email,
            boolean emailVerified, String givenName, String familyName, Collection<String> groups,
            String refreshToken, long accessExpiresAt) { }

    private record Discovery(String issuer, String tokenEndpoint, JWTParser parser) { }

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    /** Client id the frontend uses for the authorization request; the code is bound to it. */
    @ConfigProperty(name = "app.oidc.client-id")
    String clientId;

    /** Only needed when that Authentik provider is confidential. */
    @ConfigProperty(name = "app.oidc.client-secret")
    Optional<String> clientSecret;

    /** Fixed redirect URI; never taken from the request. */
    @ConfigProperty(name = "app.oidc.redirect-uri")
    String redirectUri;

    @ConfigProperty(name = "app.oidc.role-claim", defaultValue = "roles")
    String roleClaim;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Discovery discovery;

    public Tokens exchangeCode(String code, String codeVerifier) throws GrantRejectedException, Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", codeVerifier);
        return tokens(postToken(form), null);
    }

    public Tokens refresh(String refreshToken) throws GrantRejectedException, Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        return tokens(postToken(form), refreshToken);
    }

    private JsonObject postToken(Map<String, String> form) throws GrantRejectedException, Exception {
        form.put("client_id", clientId);
        clientSecret.filter(s -> !s.isBlank()).ifPresent(s -> form.put("client_secret", s));
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(discovery().tokenEndpoint()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 400 || response.statusCode() == 401) {
            throw new GrantRejectedException("Token endpoint rejected the grant: " + response.statusCode());
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token endpoint returned " + response.statusCode());
        }
        return parse(response.body());
    }

    private Tokens tokens(JsonObject response, String previousRefreshToken) throws Exception {
        String idToken = response.getString("id_token", null);
        String accessToken = response.getString("access_token", null);
        if (idToken == null && accessToken == null) {
            throw new IllegalStateException("Token response without id_token or access_token");
        }
        JWTParser parser = discovery().parser();
        JsonWebToken primary = parser.parse(idToken != null ? idToken : accessToken);
        JsonWebToken access = null;
        if (accessToken != null && idToken != null) {
            try {
                access = parser.parse(accessToken);
            } catch (Exception e) {
                log.fine("Access token is not a verifiable JWT; using id_token claims only");
            }
        }
        Collection<String> groups = groups(primary);
        if (groups.isEmpty() && access != null) {
            groups = groups(access);
        }
        long expiresIn = response.containsKey("expires_in") ? response.getJsonNumber("expires_in").longValue() : 300L;
        String refreshToken = response.getString("refresh_token", previousRefreshToken);
        return new Tokens(primary.getIssuer(), primary.getSubject(),
                string(primary, "preferred_username"), string(primary, "email"),
                Boolean.parseBoolean(String.valueOf(claim(primary, "email_verified"))),
                string(primary, "given_name"), string(primary, "family_name"),
                groups, refreshToken, System.currentTimeMillis() + expiresIn * 1000L);
    }

    private Collection<String> groups(JsonWebToken jwt) {
        Object value = claim(jwt, roleClaim);
        if (value == null && !"groups".equals(roleClaim)) {
            value = claim(jwt, "groups");
        }
        List<String> result = new ArrayList<>();
        if (value instanceof JsonArray array) {
            for (JsonValue v : array) {
                if (v instanceof JsonString s) result.add(s.getString());
            }
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(v -> result.add(String.valueOf(v)));
        } else if (value instanceof JsonString s) {
            result.add(s.getString());
        } else if (value instanceof String s) {
            result.add(s);
        }
        return result;
    }

    private static Object claim(JsonWebToken jwt, String name) {
        return jwt.getClaim(name);
    }

    private static String string(JsonWebToken jwt, String name) {
        Object value = jwt.getClaim(name);
        if (value == null) return null;
        if (value instanceof JsonString js) return js.getString();
        return value.toString();
    }

    private Discovery discovery() throws Exception {
        Discovery current = discovery;
        if (current == null) {
            synchronized (this) {
                if (discovery == null) {
                    discovery = loadDiscovery();
                }
                current = discovery;
            }
        }
        return current;
    }

    private Discovery loadDiscovery() throws Exception {
        String base = authServerUrl.endsWith("/") ? authServerUrl : authServerUrl + "/";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + ".well-known/openid-configuration"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("OIDC discovery returned " + response.statusCode());
        }
        JsonObject config = parse(response.body());
        String issuer = config.getString("issuer");
        JWTAuthContextInfo info = new JWTAuthContextInfo(config.getString("jwks_uri"), issuer);
        info.setExpectedAudience(Set.of(clientId));
        info.setSignatureAlgorithm(Set.of(SignatureAlgorithm.RS256, SignatureAlgorithm.ES256));
        return new Discovery(issuer, config.getString("token_endpoint"), new DefaultJWTParser(info));
    }

    private static JsonObject parse(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }
}
