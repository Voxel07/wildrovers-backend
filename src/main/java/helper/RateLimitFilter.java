package helper;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Logger;

import jakarta.ws.rs.container.ContainerRequestFilter;
import tools.RateLimiter;

/**
 * JAX-RS filter that runs on every request:
 * 1. Captures the real client IP (proxied or direct)
 * 2. Enforces per-endpoint rate limits
 *
 * Registered automatically via @Provider (JAX-RS scanning).
 */
@Provider
@ApplicationScoped
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger log = Logger.getLogger(RateLimitFilter.class.getName());

    @Inject
    RateLimiter rateLimiter;

    @Inject
    RequestIpCapture ipCapture;

    /**
     * Injected via Quarkus REST (RESTEasy Reactive) @Context proxy.
     * Must be a field so the proxy can be injected.
     */
    @jakarta.ws.rs.core.Context
    HttpServerRequest httpRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // 1. Resolve client IP
        String ip = resolveClientIp();
        ipCapture.setClientIp(ip);

        // 2. Rate-limit check
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        // Normalize: strip leading slash for matching consistency
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (!rateLimiter.tryConsume(ip, path, method)) {
            requestContext.abortWith(
                Response.status(429)
                    .entity("{\"status\":\"error\",\"message\":\"Zu viele Anfragen. Bitte warte einen Moment.\"}")
                    .build()
            );
        }
    }

    /**
     * Resolve the real client IP, honouring X-Forwarded-For when behind a proxy.
     * quarkus.http.proxy.proxy-address-forwarding=true is already set, so Quarkus
     * rewrites remoteAddress, but we also check X-Forwarded-For as a safety net.
     */
    private String resolveClientIp() {
        if (httpRequest != null) {
            // Try X-Forwarded-For first (leftmost IP is the original client)
            String xff = httpRequest.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String firstIp = xff.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
            // Fall back to the direct remote address
            try {
                var addr = httpRequest.remoteAddress();
                if (addr != null) {
                    return addr.hostAddress();
                }
            } catch (Exception ignored) {
            }
        }
        return "unknown";
    }
}
