package tools;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Token-bucket rate limiter keyed by "IP:endpoint".
 * Each bucket refills at a constant rate up to a maximum burst capacity.
 */
@ApplicationScoped
public class RateLimiter {

    private static final Logger log = Logger.getLogger(RateLimiter.class.getName());

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // Periodic cleanup every 5 minutes to prevent memory leak
    private long lastCleanup = System.nanoTime();
    private static final long CLEANUP_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);

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

        String key = ip + "|" + match.pathPrefix + "|" + canonicalMethod(match, method);
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(match.limit, match.windowSeconds));

        boolean allowed = bucket.tryConsume();

        // Periodic cleanup of stale entries
        periodicCleanup();

        if (!allowed) {
            log.warning("Rate limit exceeded for " + ip + " on " + path + " [" + match.pathPrefix + "] "
                    + match.limit + "/" + match.windowSeconds + "s");
        }
        return allowed;
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

    private void periodicCleanup() {
        long now = System.nanoTime();
        if (now - lastCleanup > CLEANUP_INTERVAL_NANOS) {
            synchronized (this) {
                if (now - lastCleanup > CLEANUP_INTERVAL_NANOS) {
                    lastCleanup = now;
                    // Remove buckets that haven't been used in 10 minutes
                    long staleThreshold = System.nanoTime() - TimeUnit.MINUTES.toNanos(10);
                    buckets.entrySet().removeIf(e -> e.getValue().lastAccess < staleThreshold);
                }
            }
        }
    }

    /**
     * Token bucket with smooth refill.
     */
    static class TokenBucket {
        private final double maxTokens;
        private final long windowNanos;
        private double tokens;
        private long lastRefill;
        volatile long lastAccess;

        TokenBucket(int maxTokens, int windowSeconds) {
            this.maxTokens = maxTokens;
            this.windowNanos = TimeUnit.SECONDS.toNanos(windowSeconds);
            this.tokens = maxTokens; // start full
            this.lastRefill = System.nanoTime();
            this.lastAccess = lastRefill;
        }

        synchronized boolean tryConsume() {
            refill();
            lastAccess = System.nanoTime();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (double) (now - lastRefill) / (double) windowNanos;
            tokens = Math.min(maxTokens, tokens + elapsed * maxTokens);
            lastRefill = now;
        }
    }
}
