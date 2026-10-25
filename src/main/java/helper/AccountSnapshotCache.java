package helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import model.User;
import orm.UserOrm;

import java.util.concurrent.TimeUnit;

/**
 * Short-lived copy of the account fields the authentication layer needs (role,
 * usability, token version), so that authenticating a request does not load the
 * account from the database every time. Writes that change these fields call
 * {@link #invalidate(Long)}; other instances see the change within the TTL.
 * {@link UserPrincipalResolver} still re-checks the account against the database
 * whenever a resource works with the current user.
 */
@ApplicationScoped
public class AccountSnapshotCache {

    public record Snapshot(Long id, String role, boolean usable, int tokenVersion) {
        static Snapshot of(User user) {
            return new Snapshot(user.getId(), user.getRole(), AccountStatus.isUsable(user), user.getTokenVersion());
        }
    }

    private static final Snapshot MISSING = new Snapshot(null, null, false, -1);

    private final Cache<Long, Snapshot> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    @Inject
    UserOrm userOrm;

    /** The cached snapshot, loading it if needed; {@code null} for unknown accounts. Needs an active request context. */
    public Snapshot get(Long userId) {
        if (userId == null) return null;
        Snapshot snapshot = cache.get(userId, id -> {
            User user = userOrm.findById(id);
            return user != null ? Snapshot.of(user) : MISSING;
        });
        return snapshot == MISSING ? null : snapshot;
    }

    public void invalidate(Long userId) {
        if (userId != null) cache.invalidate(userId);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
