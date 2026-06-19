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
import java.util.logging.Logger;

/**
 * Rate limiter using Bucket4j and backed by a Caffeine cache for auto-cleanup.
 * Enforces dynamic limits per-endpoint based on user roles and identity.
 */
@ApplicationScoped
public class RateLimiter {

    private static final Logger log = Logger.getLogger(RateLimiter.class.getName());

    @Inject
    SecurityIdentity identity;

    // Cache to store the rate-limit buckets with an idle expiration of 10 minutes
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    /**
     * Predefined rate-limit configurations per endpoint pattern.
     * Order matters: first match wins.
     */
    public enum Config {
        // ── Auth endpoints ──
        LOGIN("/user/login", "POST", 10, 60),           // 10 login attempts per minute
        SIGNUP("/user", "PUT", 3, 60),                  // 3 signups per minute
        LOGOUT("/user/logout", "POST", 20, 60),

        // ── Secret / password-reset ──
        PASSWORD_RESET_REQUEST("/secrets/reset-request", "POST", 3, 300),   // 3 per 5 min
        PASSWORD_RESET_CONFIRM("/secrets/reset-password", "POST", 3, 300),
        VERIFY("/secrets/verify", "POST", 5, 60),

        // ── Forum ──
        FORUM_READ("/forum", "GET", 60, 60),             // 60 reads per minute
        FORUM_WRITE("/forum", "POST|PUT|DELETE", 30, 60),// 30 writes per minute

        // ── Events / Gallery ──
        EVENTS_READ("/event", "GET", 60, 60),
        EVENTS_WRITE("/event", "POST|PUT|DELETE", 20, 60),
        GALLERY_READ("/gallery", "GET", 60, 60),
        GALLERY_WRITE("/gallery", "POST|PUT|DELETE", 15, 60),

        // ── User profile (photo/background uploads) ──
        USER_PROFILE_WRITE("/user/me", "POST|PUT", 20, 60),

        // ── Default fallback ──
        DEFAULT("/*", "*", 100, 60);                     // 100 requests per minute

        public final String pathPrefix;
        public final String methods;   // "*" means any, or pipe-separated like "GET|POST"
        public final int limit;
        public final int windowSeconds;

        Config(String pathPrefix, String methods, int limit, int windowSeconds) {
            this.pathPrefix = pathPrefix;
            this.methods = methods;
            this.limit = limit;
            this.windowSeconds = windowSeconds;
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
            log.warning("Rate limit exceeded for " + identityKey + " (IP: " + ip + ", Role: " + role + ") on " + path 
                    + " [" + match.pathPrefix + "] limit is " + (int) Math.round(match.limit * multiplier) + "/" + match.windowSeconds + "s");
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
            if (path.startsWith(c.pathPrefix)) {
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
