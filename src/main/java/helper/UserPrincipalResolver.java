package helper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import model.User;
import orm.UserOrm;
import tools.GeoIPService;
import io.quarkus.cache.CacheResult;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class UserPrincipalResolver {
    private static final Logger log = Logger.getLogger(UserPrincipalResolver.class.getName());

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @Inject
    UserOrm userOrm;

    @Inject
    RequestIpCapture ipCapture;

    @Inject
    GeoIPService geoIPService;

    @Inject
    UserPrincipalResolver self;

    public User resolveUser() {
        if (identity.isAnonymous()) {
            log.warning("Cannot resolve user: Current request is anonymous.");
            return null;
        }

        String username = identity.getPrincipal().getName();
        boolean isLocalJwt = Boolean.TRUE.equals(identity.getAttribute("local-jwt"));
        String email = null;
        String firstName = null;
        String lastName = null;

        if (isLocalJwt) {
            email = identity.getAttribute("email"); // set by LocalJwtAuthMechanism
        } else {
            try {
                email = jwt.getClaim("email"); // OIDC access token
                firstName = jwt.getClaim("given_name");
                lastName = jwt.getClaim("family_name");
            } catch (Exception e) {
                // Running under @TestSecurity or without a real JWT — resolve by username
                log.fine("JWT claims not available, resolving by username: " + username);
            }
        }
        Set<String> groups = identity.getRoles();
        String mappedRole = mapOidcGroupsToRole(groups);

        // Fetch user from DB (or get cached result) via CDI proxy to trigger @CacheResult
        User user = self.fetchAndCacheUser(username, email, firstName, lastName, mappedRole, isLocalJwt);

        if (user != null && user.getIsBlocked()) {
            log.warning("User " + user.getUserName() + " is blocked. Denying access.");
            throw new jakarta.ws.rs.WebApplicationException(
                jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.FORBIDDEN)
                        .entity("Dein Account wurde gesperrt.")
                        .build()
            );
        }

        // ── IP country-change detection ──
        checkCountryMismatch(user);

        return user;
    }

    @CacheResult(cacheName = "resolved-users")
    public User fetchAndCacheUser(String username, String email, String firstName, String lastName, String mappedRole, boolean isLocalJwt) {
        log.info("Resolving user from DB for principal: " + username + " (email: " + email + ")");
        User user = null;
        if (email != null && !email.isBlank()) {
            user = userOrm.findByEmail(email);
        }
        
        if (user == null && username != null && !username.isBlank()) {
            user = userOrm.findByUsername(username);
        }

        // If user is not in database, JIT-provision them (only with valid OIDC data)
        if (user == null && email != null && !email.isBlank()) {
            log.info("User not found in DB. JIT-provisioning a new user...");
            user = userOrm.createOidcUser(username, email, firstName, lastName, mappedRole);
        }

        if (user == null) {
            return null;
        }

        // Update roles if they changed in Authentik (skip for local logins)
        if (!isLocalJwt && mappedRole != null) {
            if (!mappedRole.equals(user.getRole())) {
                log.info("User role changed in identity provider. Syncing role: " + user.getRole() + " -> " + mappedRole);
                user.setRole(mappedRole);
                userOrm.updateUserRole(user.getId(), mappedRole);
            }
        }

        return user;
    }

    public Long resolveUserId() {
        User user = resolveUser();
        return user != null ? user.getId() : null;
    }

    private String mapOidcGroupsToRole(Set<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return "Besucher";
        }
        
        // Match highest privilege group (case-insensitive)
        boolean isAdmin = false;
        boolean isVorstand = false;
        boolean isMitglied = false;
        boolean isFrischling = false;

        for (String g : groups) {
            String lower = g.toLowerCase();
            if (lower.contains("admin")) {
                isAdmin = true;
            } else if (lower.contains("vorstand") || lower.contains("aldermen")) {
                isVorstand = true;
            } else if (lower.contains("mitglied") || lower.contains("member") || lower.contains("user") || lower.contains("wrw")) {
                isMitglied = true;
            } else if (lower.contains("frischling") || lower.contains("freshman")) {
                isFrischling = true;
            }
        }

        if (isAdmin) return "Admin";
        if (isVorstand) return "Vorstand";
        if (isMitglied) return "Mitglied";
        if (isFrischling) return "Frischling";
        
        return "Besucher";
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
