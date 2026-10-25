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
import tools.Telemetry;

/**
 * Resolves the database account of the current request. The account is
 * selected exclusively by the immutable user id that the authentication layer
 * bound to the token ({@link LocalJwtIdentityService} for local JWTs,
 * {@link OidcIdentityService} for OIDC tokens). Account status is re-checked
 * against the database on every request.
 */
@ApplicationScoped
public class UserPrincipalResolver {

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

    /** Reports each user/country change once per hour instead of on every request. */
    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> reportedCountryChanges =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofHours(1))
                    .maximumSize(10_000)
                    .build();

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
            Telemetry.warn("Authenticated identity without bound account id; denying user resolution");
        }

        if (user != null && !AccountStatus.isUsable(user)) {
            Telemetry.warn("User is blocked or inactive; denying access", "user.id", user.getId(), "security.event", "blocked_account");
            throw forbidden(AccountStatus.BLOCKED_MESSAGE);
        }

        if (user != null) {
            Telemetry.attrs("enduser.id", String.valueOf(user.getId()), "enduser.role", user.getRole());
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
                .header(AccountStatus.STATUS_HEADER, AccountStatus.STATUS_DENIED)
                .entity(message)
                .build());
    }

    /**
     * Logs when a request comes from another country than the last login. This is
     * only an audit signal: mobile networks, VPNs and travel change countries all
     * the time, so it never ends the session.
     */
    private void checkCountryMismatch(User user) {
        if (user == null) return;
        String savedCountry = user.getLastLoginCountry();
        if (savedCountry == null || savedCountry.isBlank()
                || "Unknown".equals(savedCountry) || "Local".equals(savedCountry)) {
            return;
        }

        String currentIp = ipCapture.getClientIp();
        if (currentIp == null || "unknown".equals(currentIp)) return;

        String currentCountry = geoIPService.getCountry(currentIp);
        if ("Unknown".equals(currentCountry) || "Local".equals(currentCountry)) return;

        if (!currentCountry.equals(savedCountry)
                && reportedCountryChanges.asMap().putIfAbsent(user.getId() + "|" + currentCountry, Boolean.TRUE) == null) {
            Telemetry.warn("Country change since last login", "security.event", "country_change",
                    "user.id", user.getId(), "geo.country.previous", savedCountry, "geo.country", currentCountry);
        }
    }
}
