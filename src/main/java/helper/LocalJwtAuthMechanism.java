package helper;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Base64;
import java.util.Collections;
import java.util.Set;

/**
 * Routes locally-signed JWTs to a dedicated identity provider. Database access
 * happens there on a worker thread; OIDC tokens continue to the OIDC mechanism.
 */
@ApplicationScoped
public class LocalJwtAuthMechanism implements HttpAuthenticationMechanism {

    @Override
    public int getPriority() {
        return 2500;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Uni.createFrom().nullItem();
        }

        String token = authHeader.substring(7).trim();
        if (!isWildroversToken(token)) {
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
