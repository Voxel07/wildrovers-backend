package orm;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import model.Users.AuthSession;
import orm.Secrets.SecretOrm;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.AesGcm;
import tools.Time;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * Refresh sessions behind the HttpOnly {@code wr_rt} cookie. Each refresh
 * rotates the token; presenting a replaced token again (outside a short grace
 * window for parallel tabs) is treated as theft and ends the session.
 */
@ApplicationScoped
public class AuthSessionOrm {
    private static final Logger log = Logger.getLogger(AuthSessionOrm.class.getName());

    /** Parallel requests from several tabs may present the token that was just replaced. */
    static final long ROTATION_GRACE_MS = 30_000L;

    public enum Outcome { ROTATED, GRACE, INVALID, REUSED, EXPIRED }

    /** Result of a refresh lookup; {@code newToken} is set only for {@link Outcome#ROTATED}. */
    public record Refresh(Outcome outcome, AuthSession session, String newToken) { }

    @Inject
    EntityManager em;

    @ConfigProperty(name = "app.auth.refresh-token-ttl", defaultValue = "7d")
    Duration idleTtl;

    @ConfigProperty(name = "app.auth.session-max-age", defaultValue = "30d")
    Duration maxAge;

    @ConfigProperty(name = "app.auth.session-encryption-key")
    String encryptionKey;

    /** Creates a session and returns the raw refresh token for the cookie. */
    @Transactional
    public String create(Long userId, int tokenVersion, String loginMethod, String idpRefreshToken,
            Long idpAccessExpiresAt, String userAgent) {
        String token = AesGcm.randomToken();
        long now = Time.currentTimeInMillis();
        AuthSession session = new AuthSession();
        session.setUserId(userId);
        session.setTokenHash(SecretOrm.hashResetToken(token));
        session.setTokenVersion(tokenVersion);
        session.setLoginMethod(loginMethod);
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setIdpRefreshTokenEncrypted(encrypt(idpRefreshToken));
        session.setIdpAccessExpiresAt(idpAccessExpiresAt);
        session.setUserAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        em.persist(session);
        return token;
    }

    /** Validates the presented refresh token and rotates it. */
    @Transactional
    public Refresh rotate(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return new Refresh(Outcome.INVALID, null, null);
        }
        String hash = SecretOrm.hashResetToken(presentedToken);
        long now = Time.currentTimeInMillis();

        AuthSession session = findBy("tokenHash", hash);
        if (session == null) {
            AuthSession replaced = findBy("previousTokenHash", hash);
            if (replaced == null || replaced.getRevokedAt() != null) {
                return new Refresh(Outcome.INVALID, null, null);
            }
            if (replaced.getRotatedAt() != null && now - replaced.getRotatedAt() <= ROTATION_GRACE_MS) {
                return new Refresh(Outcome.GRACE, replaced, null);
            }
            log.warning("Refresh token reuse detected for session " + replaced.getId() + " of user "
                    + replaced.getUserId() + "; ending the session.");
            replaced.setRevokedAt(now);
            return new Refresh(Outcome.REUSED, replaced, null);
        }
        if (session.getRevokedAt() != null) {
            return new Refresh(Outcome.INVALID, null, null);
        }
        if (now - session.getLastUsedAt() > idleTtl.toMillis() || now - session.getCreatedAt() > maxAge.toMillis()) {
            session.setRevokedAt(now);
            return new Refresh(Outcome.EXPIRED, session, null);
        }
        String token = AesGcm.randomToken();
        session.setPreviousTokenHash(hash);
        session.setTokenHash(SecretOrm.hashResetToken(token));
        session.setRotatedAt(now);
        session.setLastUsedAt(now);
        return new Refresh(Outcome.ROTATED, session, token);
    }

    @Transactional
    public void revoke(Long sessionId) {
        AuthSession session = sessionId != null ? em.find(AuthSession.class, sessionId) : null;
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(Time.currentTimeInMillis());
        }
    }

    /** Ends the session behind a refresh cookie, if any (logout). Returns its account id. */
    @Transactional
    public Long revokeByToken(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) return null;
        String hash = SecretOrm.hashResetToken(presentedToken);
        AuthSession session = findBy("tokenHash", hash);
        if (session == null) session = findBy("previousTokenHash", hash);
        if (session == null || session.getRevokedAt() != null) return null;
        session.setRevokedAt(Time.currentTimeInMillis());
        return session.getUserId();
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        em.createQuery("UPDATE AuthSession s SET s.revokedAt = :now WHERE s.userId = :uid AND s.revokedAt IS NULL")
                .setParameter("now", Time.currentTimeInMillis())
                .setParameter("uid", userId)
                .executeUpdate();
    }

    /** Stores the identity provider's rotated refresh token after a successful re-check there. */
    @Transactional
    public void updateIdpTokens(Long sessionId, String idpRefreshToken, long idpAccessExpiresAt) {
        AuthSession session = em.find(AuthSession.class, sessionId);
        if (session != null) {
            session.setIdpRefreshTokenEncrypted(encrypt(idpRefreshToken));
            session.setIdpAccessExpiresAt(idpAccessExpiresAt);
        }
    }

    public String decryptIdpRefreshToken(AuthSession session) {
        String value = session.getIdpRefreshTokenEncrypted();
        if (value == null) return null;
        try {
            return AesGcm.decrypt(encryptionKey, value);
        } catch (Exception e) {
            log.warning("Stored identity-provider token of session " + session.getId() + " cannot be decrypted");
            return null;
        }
    }

    /** Deletes sessions that can no longer be used. */
    @Scheduled(cron = "0 15 4 * * ?")
    @Transactional
    void purgeExpired() {
        long now = Time.currentTimeInMillis();
        int removed = em.createQuery("DELETE FROM AuthSession s WHERE s.revokedAt IS NOT NULL OR s.lastUsedAt < :idle OR s.createdAt < :max")
                .setParameter("idle", now - idleTtl.toMillis())
                .setParameter("max", now - maxAge.toMillis())
                .executeUpdate();
        if (removed > 0) log.info("Removed " + removed + " expired auth sessions");
    }

    private AuthSession findBy(String field, String hash) {
        List<AuthSession> result = em.createQuery("SELECT s FROM AuthSession s WHERE s." + field + " = :h", AuthSession.class)
                .setParameter("h", hash)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList();
        return result.isEmpty() ? null : result.get(0);
    }

    private String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AesGcm.encrypt(encryptionKey, value);
        } catch (Exception e) {
            throw new IllegalStateException("Identity-provider token could not be encrypted", e);
        }
    }
}
