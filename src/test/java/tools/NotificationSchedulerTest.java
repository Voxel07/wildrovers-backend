package tools;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.User;
import model.notifications.NotificationPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class NotificationSchedulerTest {
    @Inject EntityManager em;
    @Inject NotificationService notificationService;
    @Inject NotificationScheduler scheduler;
    @Inject MockMailbox mailbox;

    @BeforeEach
    @Transactional
    void setup() {
        mailbox.clear();
        em.createQuery("DELETE FROM NotificationReceipt").executeUpdate();
        em.createQuery("DELETE FROM NotificationItem").executeUpdate();
        em.createQuery("DELETE FROM NotificationPreference p WHERE p.user.id IN (970, 971)").executeUpdate();
        em.createQuery("DELETE FROM EmailSendLog").executeUpdate();
        insertUserIfMissing(970L, "mailSubscriber", "mail-subscriber@test.local");
        insertUserIfMissing(971L, "mailCreator", "mail-creator@test.local");
    }

    @Test
    @Transactional
    void eventReceiptIsDeliveredThroughTheMailer() {
        NotificationPreference preference = new NotificationPreference();
        preference.setUser(em.find(User.class, 970L));
        preference.setResourceType("EVENT");
        preference.setEmailEnabled(true);
        preference.setWebhookEnabled(false);
        preference.setUpdatedAt(System.currentTimeMillis());
        em.persist(preference);

        notificationService.record("EVENT", "CREATED", 998877L, "Mail delivery event", "/events",
                System.currentTimeMillis() + 86_400_000L, true, 971L);
        em.flush();

        scheduler.deliverEmails();
        em.flush();

        assertEquals(1, mailbox.getMailsSentTo("mail-subscriber@test.local").size());
        Long sentAt = em.createQuery(
                "SELECT r.emailLastSentAt FROM NotificationReceipt r WHERE r.user.id = 970",
                Long.class).getSingleResult();
        assertNotNull(sentAt);
        assertEquals("SENT", em.createQuery(
                "SELECT l.status FROM EmailSendLog l WHERE l.recipient = 'mail-subscriber@test.local'",
                String.class).getSingleResult());
    }

    private void insertUserIfMissing(Long id, String username, String email) {
        if (em.find(User.class, id) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) "
                    + "VALUES (:id,:email,:username,'test1234','Mail','Test','Mitglied',true,0,false,false)")
                    .setParameter("id", id)
                    .setParameter("email", email)
                    .setParameter("username", username)
                    .executeUpdate();
        }
    }
}
