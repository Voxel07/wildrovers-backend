package helper;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Replaces the raw OIDC group roles with the role of the bound database
 * account (see {@link OidcIdentityService}). Locally issued JWTs are already
 * bound and checked by {@link LocalJwtIdentityService}.
 */
@ApplicationScoped
public class OidcRoleAugmentor implements SecurityIdentityAugmentor {

    @Inject
    OidcIdentityService oidcIdentityService;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || Boolean.TRUE.equals(identity.getAttribute("local-jwt"))) {
            return Uni.createFrom().item(identity);
        }
        return context.runBlocking(() -> oidcIdentityService.bind(identity));
    }
}
