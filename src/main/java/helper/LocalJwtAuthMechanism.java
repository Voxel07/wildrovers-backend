package helper;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Base64;
import java.util.Collections;
import java.util.Set;

/**
 * Routes locally-signed JWTs to a dedicated identity provider. Database access
 * happens there on a worker thread; OIDC tokens continue to the OIDC mechanism.
 * Browsers send the token in the HttpOnly {@link AuthCookies#ACCESS_COOKIE}
 * cookie; API clients may still send it as a bearer token.
 */
@ApplicationScoped
public class LocalJwtAuthMechanism implements HttpAuthenticationMechanism {

    private static final Set<String> SESSION_ENDPOINTS = Set.of("/auth/login", "/auth/oidc", "/auth/refresh", "/auth/logout");

    @Override
    public int getPriority() {
        return 2500;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String token = null;
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (!SESSION_ENDPOINTS.contains(context.normalizedPath())) {
            // The session endpoints work from the refresh cookie; an expired access
            // cookie must not turn them into a 401.
            Cookie cookie = context.request().getCookie(AuthCookies.ACCESS_COOKIE);
            if (cookie != null && !cookie.getValue().isBlank()) {
                token = cookie.getValue();
            }
        }
        if (token == null || !isWildroversToken(token)) {
            return Uni.createFrom().nullItem();
        }

        return identityProviderManager.authenticate(new LocalJwtAuthenticationRequest(token));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(LocalJwtAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(
                new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "Bearer"));
    }

    private boolean isWildroversToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String json = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                return json.contains("\"iss\":\"wildrovers\"")
                        || json.contains("\"iss\" : \"wildrovers\"");
            }
        } catch (Exception ignored) {
            // Malformed tokens are left for the remaining mechanisms to reject.
        }
        return false;
    }
}
