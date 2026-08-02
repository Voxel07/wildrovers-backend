package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.notifications.EmailSendLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EmailQuotaService {
    @Inject EntityManager em;
    @ConfigProperty(name = "notification.mail.max-per-hour", defaultValue = "40") int maxPerHour;
    @ConfigProperty(name = "notification.mail.max-per-recipient-day", defaultValue = "12") int maxPerRecipientDay;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public synchronized Long reserve(String recipient, String mailType) {
        long now = System.currentTimeMillis();
        Long hourly = em.createQuery("SELECT COUNT(l) FROM EmailSendLog l WHERE l.createdAt >= :since AND l.status IN ('RESERVED','SENT')", Long.class)
                .setParameter("since", now - 3_600_000L).getSingleResult();
        Long daily = em.createQuery("SELECT COUNT(l) FROM EmailSendLog l WHERE LOWER(l.recipient) = :recipient AND l.createdAt >= :since AND l.status IN ('RESERVED','SENT')", Long.class)
                .setParameter("recipient", recipient.toLowerCase()).setParameter("since", now - 86_400_000L).getSingleResult();
        if (hourly >= maxPerHour || daily >= maxPerRecipientDay) return null;
        EmailSendLog log = new EmailSendLog();
        log.setRecipient(recipient.toLowerCase());
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
}
