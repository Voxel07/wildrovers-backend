package tools;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter using Bucket4j and backed by a Caffeine cache for auto-cleanup.
 * Enforces dynamic limits per-endpoint based on user roles and identity.
 */
@ApplicationScoped
public class RateLimiter {


    @Inject
    SecurityIdentity identity;

    // Rate-limit buckets expire after 10 idle minutes. The size cap keeps rotating client
    // addresses (e.g. a large IPv6 range) from growing the cache without bound.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    /**
     * Predefined rate-limit configurations per endpoint pattern.
     * Order matters: first match wins.
     */
    public enum Config {
        // ── Auth endpoints ──
        LOGIN("/auth/login", "POST", 10, 60),           // 10 login attempts per minute
        OIDC_LOGIN("/auth/oidc", "POST", 10, 60),
        // Every open tab refreshes about every 15 minutes; the slack covers several devices behind one IP.
        SESSION_REFRESH("/auth/refresh", "POST", 30, 60),
        LOGOUT("/auth/logout", "POST", 20, 60),
        RESEND_VERIFICATION("/user/resend-verification", "POST", 3, 300),
        SIGNUP("/user", "PUT", 3, 60, true),            // 3 signups per minute (exactly PUT /user)

        // ── Secret / password-reset ──
        PASSWORD_RESET_REQUEST("/secrets/reset-request", "POST", 3, 300),   // 3 per 5 min
        PASSWORD_RESET_CONFIRM("/secrets/reset-password", "POST", 3, 300),
        VERIFY("/secrets/verify", "POST", 5, 60),

        // ── Forum ──
        // Images have their own bucket: a single post can embed many pictures.
        FORUM_IMAGES("/forum/img", "GET", 300, 60),
        FORUM_READ("/forum", "GET", 60, 60),             // 60 reads per minute
        FORUM_WRITE("/forum", "POST|PUT|DELETE", 30, 60),// 30 writes per minute

        // ── Events / Gallery ──
        EVENTS_READ("/event", "GET", 60, 60),
        EVENTS_WRITE("/event", "POST|PUT|DELETE", 20, 60),
        GALLERY_READ("/gallery", "GET", 60, 60),
        GALLERY_WRITE("/gallery", "POST|PUT|DELETE", 15, 60),

        // ── User profile (photo/background uploads) ──
        NOTIFICATION_PREFERENCES("/user/me/notifications", "GET|POST|PUT|DELETE", 10, 600),
        USER_PROFILE_WRITE("/user/me", "POST|PUT", 20, 60),

        // ── Default fallback ──
        DEFAULT("/*", "*", 100, 60);                     // 100 requests per minute

        public final String pathPrefix;
        public final String methods;   // "*" means any, or pipe-separated like "GET|POST"
        public final int limit;
        public final int windowSeconds;
        /** Match only the path itself, not the paths below it. */
        public final boolean exact;

        Config(String pathPrefix, String methods, int limit, int windowSeconds) {
            this(pathPrefix, methods, limit, windowSeconds, false);
        }

        Config(String pathPrefix, String methods, int limit, int windowSeconds, boolean exact) {
            this.pathPrefix = pathPrefix;
            this.methods = methods;
            this.limit = limit;
            this.windowSeconds = windowSeconds;
            this.exact = exact;
        }

        /** Prefixes match whole path segments: "/user" covers "/user/me" but not "/users". */
        boolean matchesPath(String path) {
            String normalized = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            if (normalized.equals(pathPrefix)) return true;
            return !exact && normalized.startsWith(pathPrefix + "/");
        }
    }

    /**
     * Try to consume one token for the given (ip, path, method).
     *
     * @return true if allowed, false if rate-limited
     */
    public boolean tryConsume(String ip, String path, String method) {
        // Find the matching config
        Config match = findConfig(path, method);

        // Determine user identity key and role multiplier
        String role = getRoleName();
        double multiplier = getRoleMultiplier(role);
        String identityKey = getIdentityKey(ip, role);

        String key = identityKey + "|" + match.pathPrefix + "|" + canonicalMethod(match, method);

        Bucket bucket = buckets.get(key, k -> createBucket(match, multiplier));

        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            Telemetry.warn("Rate limit exceeded", "security.event", "rate_limited", "rate_limit.key", identityKey,
                    "enduser.role", role, "rate_limit.path_prefix", match.pathPrefix,
                    "rate_limit.limit", (int) Math.round(match.limit * multiplier), "rate_limit.window_seconds", match.windowSeconds);
        }
        return allowed;
    }

    private Bucket createBucket(Config match, double multiplier) {
        int capacity = (int) Math.round(match.limit * multiplier);
        if (capacity <= 0) {
            capacity = 1;
        }
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofSeconds(match.windowSeconds))
                        .build())
                .build();
    }

    private Config findConfig(String path, String method) {
        for (Config c : Config.values()) {
            if (c == Config.DEFAULT) continue;
            if (c.matchesPath(path)) {
                if ("*".equals(c.methods)) return c;
                for (String m : c.methods.split("\\|")) {
                    if (m.equalsIgnoreCase(method)) return c;
                }
                // Path matched but method didn't — continue to next config
            }
        }
        return Config.DEFAULT;
    }

    /**
     * Canonicalize the method part of the key. For configs that only differentiate
     * read vs write (like FORUM), use "READ" / "WRITE" so GET for FORUM_READ and
     * POST for FORUM_WRITE don't share the same bucket.
     */
    private String canonicalMethod(Config config, String method) {
        if (config == Config.DEFAULT) return "*";
        if ("*".equals(config.methods)) return "*";
        return method.toUpperCase();
    }

    private String getRoleName() {
        if (identity == null || identity.isAnonymous()) {
            return "Anonymous";
        }
        // Match highest privilege role
        if (identity.hasRole("Admin")) return "Admin";
        if (identity.hasRole("Vorstand")) return "Vorstand";
        if (identity.hasRole("Mitglied")) return "Mitglied";
        if (identity.hasRole("Frischling")) return "Frischling";
        if (identity.hasRole("Besucher")) return "Besucher";
        return "Besucher";
    }

    private double getRoleMultiplier(String role) {
        switch (role) {
            case "Admin":
                return 100.0; // Admins get massive limits
            case "Vorstand":
                return 5.0;   // 5x base limit
            case "Mitglied":
                return 3.0;   // 3x base limit
            case "Frischling":
                return 2.0;   // 2x base limit
            case "Besucher":
                return 1.2;   // 1.2x base limit
            default:
                return 1.0;   // Anonymous/unauthenticated clients get 1x
        }
    }

    private String getIdentityKey(String ip, String role) {
        if (identity != null && !identity.isAnonymous()) {
            return "user:" + identity.getPrincipal().getName() + ":" + role;
        }
        return "ip:" + ip + ":" + role;
    }
}
