package helper;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class OidcRoleAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        
        return Uni.createFrom().item(() -> {
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
            
            // Map raw OIDC groups/roles to standard database roles
            Set<String> groups = identity.getRoles();
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

            if (isAdmin) {
                builder.addRole("Admin");
            }
            if (isVorstand) {
                builder.addRole("Vorstand");
            }
            if (isMitglied) {
                builder.addRole("Mitglied");
            }
            if (isFrischling) {
                builder.addRole("Frischling");
            }
            
            // Every authenticated user is at least a visitor
            builder.addRole("Besucher");
            
            return builder.build();
        });
    }
}
