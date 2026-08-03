package helper;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LocalJwtIdentityProvider implements IdentityProvider<LocalJwtAuthenticationRequest> {

    @Inject
    LocalJwtIdentityService identityService;

    @Override
    public Class<LocalJwtAuthenticationRequest> getRequestType() {
        return LocalJwtAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(LocalJwtAuthenticationRequest request,
            AuthenticationRequestContext context) {
        return context.runBlocking(() -> identityService.authenticate(request.token()));
    }
}
