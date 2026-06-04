package helper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import model.User;
import orm.UserOrm;
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

    public User resolveUser() {
        if (identity.isAnonymous()) {
            log.warning("Cannot resolve user: Current request is anonymous.");
            return null;
        }

        // Get OIDC identity info
        String username = identity.getPrincipal().getName();
        String email = jwt.getClaim("email");
        
        log.info("Resolving user for principal: " + username + " (email: " + email + ")");

        User user = null;
        if (email != null && !email.isBlank()) {
            user = userOrm.findByEmail(email);
        }
        
        if (user == null && username != null && !username.isBlank()) {
            user = userOrm.findByUsername(username);
        }

        // If user is not in database, JIT-provision them
        if (user == null) {
            log.info("User not found in DB. JIT-provisioning a new user...");
            String firstName = jwt.getClaim("given_name");
            String lastName = jwt.getClaim("family_name");
            
            // Map the OIDC groups/roles to local DB roles
            Set<String> groups = identity.getRoles();
            String mappedRole = mapOidcGroupsToRole(groups);
            
            user = userOrm.createOidcUser(username, email, firstName, lastName, mappedRole);
        } else {
            // Update roles if they changed in Authentik
            Set<String> groups = identity.getRoles();
            String currentMappedRole = mapOidcGroupsToRole(groups);
            if (!currentMappedRole.equals(user.getRole())) {
                log.info("User role changed in identity provider. Syncing role: " + user.getRole() + " -> " + currentMappedRole);
                user.setRole(currentMappedRole);
                userOrm.updateUser(user);
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
}
