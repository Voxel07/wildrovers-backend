package helper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

/**
 * Builds the two HttpOnly session cookies. The access cookie carries a short-lived
 * JWT and is sent on every API call; the refresh cookie carries only an opaque
 * random token and is scoped to {@code /auth}, so ordinary API requests never
 * transmit it. JavaScript can read neither.
 */
@ApplicationScoped
public class AuthCookies {

    public static final String ACCESS_COOKIE = "wr_at";
    public static final String REFRESH_COOKIE = "wr_rt";
    public static final String REFRESH_PATH = "/auth";

    @ConfigProperty(name = "app.auth.cookie.secure", defaultValue = "true")
    boolean secure;

    @ConfigProperty(name = "app.auth.cookie.same-site", defaultValue = "Strict")
    String sameSite;

    @ConfigProperty(name = "app.auth.cookie.domain")
    Optional<String> domain;

    @ConfigProperty(name = "app.auth.access-token-ttl", defaultValue = "15m")
    Duration accessTokenTtl;

    @ConfigProperty(name = "app.auth.refresh-token-ttl", defaultValue = "7d")
    Duration refreshTokenTtl;

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    public NewCookie access(String jwt) {
        return build(ACCESS_COOKIE, jwt, "/", (int) accessTokenTtl.toSeconds());
    }

    public NewCookie refresh(String token) {
        return build(REFRESH_COOKIE, token, REFRESH_PATH, (int) refreshTokenTtl.toSeconds());
    }

    public NewCookie clearAccess() {
        return build(ACCESS_COOKIE, "", "/", 0);
    }

    public NewCookie clearRefresh() {
        return build(REFRESH_COOKIE, "", REFRESH_PATH, 0);
    }

    private NewCookie build(String name, String value, String path, int maxAge) {
        NewCookie.Builder builder = new NewCookie.Builder(name)
                .value(value)
                .path(path)
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite());
        domain.filter(d -> !d.isBlank()).ifPresent(builder::domain);
        return builder.build();
    }

    private NewCookie.SameSite sameSite() {
        return switch (sameSite.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "lax" -> NewCookie.SameSite.LAX;
            case "none" -> NewCookie.SameSite.NONE;
            default -> NewCookie.SameSite.STRICT;
        };
    }
}
