package helper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import model.User;
import orm.UserOrm;
import tools.GeoIPService;
import java.util.logging.Logger;

/**
 * Resolves the database account of the current request. The account is
 * selected exclusively by the immutable user id that the authentication layer
 * bound to the token ({@link LocalJwtIdentityService} for local JWTs,
 * {@link OidcIdentityService} for OIDC tokens). Account status is re-checked
 * against the database on every request.
 */
@ApplicationScoped
public class UserPrincipalResolver {
    private static final Logger log = Logger.getLogger(UserPrincipalResolver.class.getName());

    @Inject
    SecurityIdentity identity;

    @Inject
    UserOrm userOrm;

    @Inject
    RequestIpCapture ipCapture;

    @Inject
    GeoIPService geoIPService;

    @Inject
    RequestUserCache requestUserCache;

    public User resolveUser() {
        if (identity.isAnonymous()) {
            return null;
        }
        if (requestUserCache.isResolved()) {
            return requestUserCache.getCachedUser();
        }

        String denied = identity.getAttribute(AccountStatus.DENIED_ATTRIBUTE);
        if (denied != null) {
            throw forbidden(denied);
        }

        User user = null;
        Long userId = identity.getAttribute(AccountStatus.USER_ID_ATTRIBUTE);
        if (userId != null) {
            user = userOrm.findById(userId);
        } else if (LaunchMode.current() == LaunchMode.TEST) {
            // @TestSecurity identities carry no token; resolve them by test username.
            user = userOrm.findByUsername(identity.getPrincipal().getName());
        } else {
            log.warning("Authenticated identity without bound account id; denying user resolution.");
        }

        if (user != null && !AccountStatus.isUsable(user)) {
            log.warning("User " + user.getId() + " is blocked or inactive. Denying access.");
            throw forbidden(AccountStatus.BLOCKED_MESSAGE);
        }

        // ── IP country-change detection ──
        checkCountryMismatch(user);

        requestUserCache.setCachedUser(user);
        return user;
    }

    public Long resolveUserId() {
        User user = resolveUser();
        return user != null ? user.getId() : null;
    }

    private static WebApplicationException forbidden(String message) {
        return new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                .entity(message)
                .build());
    }

    /**
     * If the user's current IP resolves to a different country than the one
     * recorded at login time, the auth token may have been stolen.
     * Throws 401 to force re-login.
     */
    private void checkCountryMismatch(User user) {
        if (user == null) return;
        String savedCountry = user.getLastLoginCountry();
        if (savedCountry == null || savedCountry.isBlank()
                || "Unknown".equals(savedCountry) || "Local".equals(savedCountry)) {
            return;
        }

        String currentIp = ipCapture.getClientIp();
        if (currentIp == null || "unknown".equals(currentIp)
                || geoIPService == null) return;

        String currentCountry = geoIPService.getCountry(currentIp);
        if (currentCountry == null || "Unknown".equals(currentCountry)
                || "Local".equals(currentCountry)) return;

        if (!currentCountry.equals(savedCountry)) {
            log.warning("SECURITY: Country mismatch for user '" + user.getUserName()
                    + "'. Last login: " + savedCountry
                    + ", current: " + currentCountry
                    + " (IP: " + currentIp + "). Possible token theft — logging out.");
            throw new jakarta.ws.rs.WebApplicationException(
                jakarta.ws.rs.core.Response.status(401)
                        .entity("{\"status\":\"error\",\"message\":\"Sicherheitswarnung: Deine Sitzung wurde aus Sicherheitsgründen beendet. Bitte melde dich erneut an.\"}")
                        .build()
            );
        }
    }
}
