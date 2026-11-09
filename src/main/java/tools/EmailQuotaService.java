package tools;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.notifications.EmailSendLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Locale;

/**
 * Keeps outgoing mail below the provider's hourly limit. Every mail type has its own share
 * of the hourly budget, so a flood of sign-ups can never block password resets or
 * notifications, and notifications can never block account mails.
 */
@ApplicationScoped
public class EmailQuotaService {
    public static final String TYPE_VERIFICATION = "VERIFICATION";
    public static final String TYPE_ACCOUNT_EXISTS = "ACCOUNT_EXISTS";
    public static final String TYPE_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String TYPE_EMAIL_CHANGE = "EMAIL_CHANGE";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";

    /** Sign-up mails (verification codes and "you already have an account" notices) share one share. */
    private static final List<String> SIGNUP_TYPES = List.of(TYPE_VERIFICATION, TYPE_ACCOUNT_EXISTS);

    private static final long HOUR_MS = 3_600_000L;
    private static final long DAY_MS = 86_400_000L;

    @Inject EntityManager em;
    @ConfigProperty(name = "notification.mail.max-per-hour", defaultValue = "40") int maxPerHour;
    @ConfigProperty(name = "notification.mail.max-per-recipient-day", defaultValue = "12") int maxPerRecipientDay;
    @ConfigProperty(name = "notification.mail.max-per-hour.signup", defaultValue = "6") int maxSignupPerHour;
    @ConfigProperty(name = "notification.mail.max-per-hour.password-reset", defaultValue = "6") int maxResetPerHour;
    @ConfigProperty(name = "notification.mail.max-per-hour.email-change", defaultValue = "3") int maxEmailChangePerHour;
    @ConfigProperty(name = "notification.mail.log-retention", defaultValue = "2d") java.time.Duration logRetention;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public synchronized Long reserve(String recipient, String mailType) {
        long now = System.currentTimeMillis();
        String normalizedRecipient = recipient.trim().toLowerCase(Locale.ROOT);
        List<String> group = typeGroup(mailType);

        Long hourlyOfType = em.createQuery("SELECT COUNT(l) FROM EmailSendLog l WHERE l.createdAt >= :since "
                + "AND l.mailType IN :types AND l.status IN ('RESERVED','SENT')", Long.class)
                .setParameter("since", now - HOUR_MS).setParameter("types", group).getSingleResult();
        // Values are stored lower-case, so the index on (recipient, created_at) can be used.
        Long daily = em.createQuery("SELECT COUNT(l) FROM EmailSendLog l WHERE l.recipient = :recipient "
                + "AND l.createdAt >= :since AND l.status IN ('RESERVED','SENT')", Long.class)
                .setParameter("recipient", normalizedRecipient).setParameter("since", now - DAY_MS).getSingleResult();
        if (hourlyOfType >= hourlyShare(mailType) || daily >= maxPerRecipientDay) {
            Telemetry.event("email.quota_reached", "email.quota.kind", mailType,
                    "email.quota.hourly_of_type", hourlyOfType, "email.quota.recipient_daily", daily);
            return null;
        }
        EmailSendLog log = new EmailSendLog();
        log.setRecipient(normalizedRecipient);
        log.setMailType(mailType);
        log.setStatus("RESERVED");
        log.setCreatedAt(now);
        em.persist(log);
        em.flush();
        return log.getId();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void complete(Long reservationId, boolean sent) {
        if (reservationId == null) return;
        EmailSendLog log = em.find(EmailSendLog.class, reservationId);
        if (log != null) {
            log.setStatus(sent ? "SENT" : "FAILED");
            log.setCompletedAt(System.currentTimeMillis());
        }
    }

    /** The quota only looks one day back; older rows are dead weight. */
    @Scheduled(cron = "{notification.mail.log-purge-cron:0 45 3 * * ?}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void purgeOldLogEntries() {
        long cutoff = System.currentTimeMillis() - Math.max(logRetention.toMillis(), DAY_MS);
        int deleted = em.createQuery("DELETE FROM EmailSendLog l WHERE l.createdAt < :cutoff")
                .setParameter("cutoff", cutoff).executeUpdate();
        Telemetry.event("email.send_log_purged", "email.send_log.deleted", deleted);
    }

    /**
     * Hourly budget of one mail type. Account mails get fixed shares; notifications get what
     * the account mails do not reserve.
     */
    int hourlyShare(String mailType) {
        return switch (mailType) {
            case TYPE_VERIFICATION, TYPE_ACCOUNT_EXISTS -> maxSignupPerHour;
            case TYPE_PASSWORD_RESET -> maxResetPerHour;
            case TYPE_EMAIL_CHANGE -> maxEmailChangePerHour;
            default -> Math.max(0, maxPerHour - maxSignupPerHour - maxResetPerHour - maxEmailChangePerHour);
        };
    }

    private static List<String> typeGroup(String mailType) {
        if (SIGNUP_TYPES.contains(mailType)) return SIGNUP_TYPES;
        return List.of(mailType);
    }
}
