package tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolves IP → country using ip-api.com (free, no API key needed).
 * Results are cached for 24 hours to avoid hitting rate limits.
 *
 * ip-api.com free tier: 45 requests per minute.
 */
@ApplicationScoped
public class GeoIPService {

    private static final Logger log = Logger.getLogger(GeoIPService.class.getName());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static class CacheEntry {
        final String country;
        final long expiryNanos;
        CacheEntry(String country, long ttlMillis) {
            this.country = country;
            this.expiryNanos = System.nanoTime() + ttlMillis * 1_000_000L;
        }
        boolean isExpired() {
            return System.nanoTime() > expiryNanos;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = Duration.ofHours(24).toMillis();

    /**
     * Look up the country (ISO country name, not code) for an IP address.
     * Returns "Unknown" if the lookup fails or IP is local/private.
     */
    public String getCountry(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) {
            return "Unknown";
        }

        // Don't look up local/private IPs
        if (isPrivateOrLocal(ip)) {
            return "Local";
        }

        // Check cache
        CacheEntry cached = cache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return cached.country;
        }

        try {
            String country = lookupRemote(ip);
            cache.put(ip, new CacheEntry(country, CACHE_TTL_MS));
            return country;
        } catch (Exception e) {
            log.log(Level.WARNING, "GeoIP lookup failed for " + ip + ": " + e.getMessage());
            // Cache failures briefly so we don't hammer the API
            cache.put(ip, new CacheEntry("Unknown", 60_000));
            return "Unknown";
        }
    }

    private String lookupRemote(String ip) throws Exception {
        URI uri = URI.create("http://ip-api.com/json/" + ip + "?fields=country");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String body = response.body();
            // Parse simple JSON: {"country":"Germany"} or {"status":"fail"}
            // We do manual parsing to avoid pulling in JSON libraries just for this
            String country = extractJsonString(body, "country");
            if (country != null) {
                log.fine("GeoIP: " + ip + " → " + country);
                return country;
            }
        } else {
            log.warning("GeoIP API returned status " + response.statusCode() + " for " + ip);
        }
        return "Unknown";
    }

    /**
     * Extremely simple JSON string extractor — avoids pulling in any JSON library.
     */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /**
     * Check if an IP is private/local (no point doing GeoIP lookup).
     */
    private boolean isPrivateOrLocal(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")
                || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.") || ip.startsWith("fc") || ip.startsWith("fd")) {
            return true;
        }
        // 172.16.0.0 – 172.31.255.255
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Clear the cache (useful for testing or admin operations).
     */
    public void clearCache() {
        cache.clear();
    }
}
