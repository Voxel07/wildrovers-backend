package helper;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.Telemetry;

/**
 * Cookie sessions are sent by the browser automatically, so state-changing
 * requests that rely on them must prove they come from our own frontend:
 * <ul>
 *   <li>the {@code X-Requested-With} header must be present. Setting it from
 *   another origin requires a CORS preflight, which only the configured
 *   origins pass;</li>
 *   <li>an {@code Origin} header, when sent, must be one of those origins.</li>
 * </ul>
 * Requests with an {@code Authorization} header carry no ambient credentials
 * and are not checked.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class CsrfFilter implements ContainerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    public static final String HEADER = "X-Requested-With";

    @ConfigProperty(name = "quarkus.http.cors.origins")
    List<String> allowedOrigins;

    @Override
    public void filter(ContainerRequestContext request) {
        if (SAFE_METHODS.contains(request.getMethod())) return;
        if (request.getHeaderString("Authorization") != null) return;

        Map<String, Cookie> cookies = request.getCookies();
        String path = request.getUriInfo().getPath();
        boolean usesSessionCookie = cookies.containsKey(AuthCookies.ACCESS_COOKIE)
                || cookies.containsKey(AuthCookies.REFRESH_COOKIE)
                || path.startsWith("/auth/") || path.startsWith("auth/");
        if (!usesSessionCookie) return;

        String origin = request.getHeaderString("Origin");
        String requestedWith = request.getHeaderString(HEADER);
        if (requestedWith == null || requestedWith.isBlank()
                || (origin != null && !"null".equals(origin) && !isAllowed(origin))) {
            Telemetry.warn("CSRF check failed", "security.event", "csrf_rejected", "http.request.header.origin", origin,
                    "csrf.header_present", requestedWith != null && !requestedWith.isBlank());
            request.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("Anfrage abgelehnt (fehlender CSRF-Schutz).")
                    .build());
        }
    }

    private boolean isAllowed(String origin) {
        for (String allowed : allowedOrigins) {
            if (allowed.trim().equalsIgnoreCase(origin)) return true;
        }
        return false;
    }
}
