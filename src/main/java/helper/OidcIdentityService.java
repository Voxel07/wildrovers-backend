package helper;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import model.User;
import org.eclipse.microprofile.jwt.JsonWebToken;
import orm.UserOrm;

import java.util.logging.Logger;

/**
 * Binds an OIDC bearer token to exactly one database account via the immutable
 * issuer + subject pair and derives the request's roles from the database.
 * Mutable claims (email, preferred_username) are never used to select an
 * already bound account. Disabled accounts receive no roles.
 */
@ApplicationScoped
public class OidcIdentityService {
    private static final Logger log = Logger.getLogger(OidcIdentityService.class.getName());

    @Inject
    UserOrm userOrm;

    @Inject
    OidcRoleMapper roleMapper;

    @ActivateRequestContext
    public SecurityIdentity bind(SecurityIdentity identity) {
        if (!(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            if (LaunchMode.current() == LaunchMode.TEST) {
                return identity; // @TestSecurity identities
            }
            // Never trust roles of identities that cannot be bound to an account.
            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(identity.getPrincipal())
                    .addCredentials(identity.getCredentials())
                    .addAttribute(AccountStatus.DENIED_ATTRIBUTE, "Nicht unterstützte Anmeldung.")
                    .build();
        }
        String issuer = jwt.getIssuer();
        String subject = jwt.getSubject();
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            log.warning("OIDC token without issuer/subject rejected");
            throw new AuthenticationFailedException();
        }

        UserOrm.OidcProfile profile = new UserOrm.OidcProfile(
                issuer,
                subject,
                stringClaim(jwt, "preferred_username"),
                stringClaim(jwt, "email"),
                Boolean.TRUE.equals(booleanClaim(jwt, "email_verified")),
                stringClaim(jwt, "given_name"),
                stringClaim(jwt, "family_name"));
        String mappedRole = roleMapper.mapToRole(identity.getRoles());

        UserOrm.OidcResolution resolution;
        try {
            resolution = userOrm.resolveOidcUser(profile, mappedRole);
        } catch (RuntimeException e) {
            log.log(java.util.logging.Level.WARNING, "OIDC account resolution failed", e);
            throw new AuthenticationFailedException(e);
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(jwt)
                .addCredentials(identity.getCredentials())
                .addAttributes(identity.getAttributes());

        User user = resolution.user();
        if (user == null) {
            builder.addAttribute(AccountStatus.DENIED_ATTRIBUTE, resolution.problem());
            return builder.build();
        }
        builder.addAttribute(AccountStatus.USER_ID_ATTRIBUTE, user.getId());
        if (AccountStatus.isUsable(user)) {
            builder.addRole(user.getRole());
        } else {
            builder.addAttribute(AccountStatus.DENIED_ATTRIBUTE, AccountStatus.BLOCKED_MESSAGE);
        }
        return builder.build();
    }

    private static String stringClaim(JsonWebToken jwt, String name) {
        Object value = jwt.getClaim(name);
        if (value == null) return null;
        if (value instanceof jakarta.json.JsonString js) return js.getString();
        return value.toString();
    }

    private static Boolean booleanClaim(JsonWebToken jwt, String name) {
        Object value = jwt.getClaim(name);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        if (value == jakarta.json.JsonValue.TRUE) return true;
        if (value == jakarta.json.JsonValue.FALSE) return false;
        return Boolean.parseBoolean(value.toString());
    }
}
