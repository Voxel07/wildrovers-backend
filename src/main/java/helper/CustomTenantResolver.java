package helper;

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.logging.Logger;

@ApplicationScoped
public class CustomTenantResolver implements TenantResolver {
    private static final Logger log = Logger.getLogger(CustomTenantResolver.class.getName());

    @Override
    public String resolve(RoutingContext context) {
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String issuer = getIssuerFromToken(token);
            if ("wildrovers".equals(issuer)) {
                log.info("CustomTenantResolver: Routed request to 'local' tenant.");
                return "local";
            }
        }
        // Return null to fall back to the default (Authentik) tenant
        return null;
    }

    private String getIssuerFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payloadBase64 = parts[1];
                byte[] decoded = Base64.getUrlDecoder().decode(payloadBase64);
                String json = new String(decoded);
                // Simple search for issuer "wildrovers"
                if (json.contains("\"iss\":\"wildrovers\"") || json.contains("\"iss\" : \"wildrovers\"")) {
                    return "wildrovers";
                }
            }
        } catch (Exception e) {
            log.warning("CustomTenantResolver: Failed to parse token payload for issuer: " + e.getMessage());
        }
        return null;
    }
}
