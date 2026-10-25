package tools;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Resolves IP → country from a local MaxMind GeoLite2/GeoIP2 Country database
 * ({@code app.geoip.database}). Lookups take microseconds and never leave the
 * server, so no client IP is sent to a third party. Without a database file the
 * service answers "Unknown" and country tracking is off.
 */
@ApplicationScoped
@RegisterForReflection(targets = {
        CountryResponse.class,
        com.maxmind.geoip2.record.Continent.class,
        com.maxmind.geoip2.record.Country.class,
        com.maxmind.geoip2.record.RepresentedCountry.class,
        com.maxmind.geoip2.record.MaxMind.class,
        com.maxmind.geoip2.record.Traits.class
})
public class GeoIPService {


    @ConfigProperty(name = "app.geoip.database")
    Optional<String> databasePath;

    private DatabaseReader reader;

    @PostConstruct
    void open() {
        String path = databasePath.map(String::trim).orElse("");
        if (path.isEmpty()) {
            Telemetry.event("geoip.disabled", "reason", "no database configured (app.geoip.database)");
            return;
        }
        try {
            reader = new DatabaseReader.Builder(new File(path)).build();
            Telemetry.event("geoip.loaded");
        } catch (Exception e) {
            Telemetry.warn("GeoIP database could not be opened; country tracking is off", e);
        }
    }

    @PreDestroy
    void close() {
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
            // shutting down
        }
    }

    /**
     * Look up the country (English name) for an IP address.
     * Returns "Local" for private addresses and "Unknown" if the lookup is not possible.
     */
    public String getCountry(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) {
            return "Unknown";
        }
        InetAddress address;
        try {
            // Only literal addresses reach this point; never resolve host names.
            if (!ip.matches("[0-9a-fA-F:.]+")) return "Unknown";
            address = InetAddress.getByName(ip);
        } catch (Exception e) {
            return "Unknown";
        }
        if (isPrivateOrLocal(address)) {
            return "Local";
        }
        if (reader == null) {
            return "Unknown";
        }
        try {
            return reader.tryCountry(address)
                    .map(r -> r.getCountry().getName())
                    .filter(name -> name != null && !name.isBlank())
                    .orElse("Unknown");
        } catch (Exception e) {
            Telemetry.event("geoip.lookup_failed");
            return "Unknown";
        }
    }

    static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique local addresses fc00::/7 (Java reports them as neither site- nor link-local)
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
