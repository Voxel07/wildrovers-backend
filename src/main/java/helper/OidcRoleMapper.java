package helper;

import jakarta.enterprise.context.ApplicationScoped;
import model.Users.Roles;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps identity-provider groups to application roles using an explicit
 * allowlist. Only exact (case-insensitive) group names grant a role; there is
 * no substring matching, so groups such as "non-admin" never grant Admin.
 */
@ApplicationScoped
public class OidcRoleMapper {

    @ConfigProperty(name = "app.oidc.groups.admin", defaultValue = "Admin")
    List<String> adminGroups;

    @ConfigProperty(name = "app.oidc.groups.vorstand", defaultValue = "Vorstand")
    List<String> vorstandGroups;

    @ConfigProperty(name = "app.oidc.groups.mitglied", defaultValue = "Mitglied")
    List<String> mitgliedGroups;

    @ConfigProperty(name = "app.oidc.groups.frischling", defaultValue = "Frischling")
    List<String> frischlingGroups;

    /** Returns the highest application role granted by the given groups (Besucher if none). */
    public String mapToRole(Collection<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return Roles.VSISITOR;
        }
        Set<String> normalized = groups.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(OidcRoleMapper::normalize)
                .collect(Collectors.toSet());
        if (matches(normalized, adminGroups)) return Roles.ADMIN;
        if (matches(normalized, vorstandGroups)) return Roles.ALDERMEN;
        if (matches(normalized, mitgliedGroups)) return Roles.MEMBER;
        if (matches(normalized, frischlingGroups)) return Roles.FRESHMAN;
        return Roles.VSISITOR;
    }

    private static boolean matches(Set<String> groups, List<String> allowed) {
        if (allowed == null) return false;
        for (String candidate : allowed) {
            if (candidate != null && !candidate.isBlank() && groups.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String group) {
        return group.trim().toLowerCase(Locale.ROOT);
    }
}
