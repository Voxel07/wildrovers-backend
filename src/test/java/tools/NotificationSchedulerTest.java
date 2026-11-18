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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @org.junit.jupiter.api.AfterEach
    @Transactional
    void cleanup() {
        em.createQuery("DELETE FROM NotificationReceipt").executeUpdate();
        em.createQuery("DELETE FROM NotificationItem").executeUpdate();
        em.createQuery("DELETE FROM NotificationPreference p WHERE p.user.id IN (970, 971)").executeUpdate();
        em.createNativeQuery("DELETE FROM \"EVENT\" WHERE id IN (998877, 998878)").executeUpdate();
    }

    @Test
    @Transactional
    void eventMailRecipientsAreChosenAtTheMorningRun() {
        insertEvent(998877L, "Mail delivery event");
        notificationService.record("EVENT", "CREATED", 998877L, "Mail delivery event", "/events",
                System.currentTimeMillis() + 86_400_000L, true, 971L);
        em.flush();

        // Nothing is decided or sent when the event is created; the morning run is in the future.
        Long fanoutAt = em.createQuery(
                "SELECT i.emailFanoutAt FROM NotificationItem i WHERE i.entityId = 998877", Long.class).getSingleResult();
        assertTrue(fanoutAt > System.currentTimeMillis());
        assertEquals(0L, receiptsOf(970L));

        // Opting in after the event was created still counts: preferences are read at send time.
        subscribe(970L);
        scheduler.deliverEmails();
        em.flush();
        assertEquals(0, mailbox.getMailsSentTo("mail-subscriber@test.local").size());

        makeMorningRunDue();
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

    @Test
    @Transactional
    void eventCreatedAndDeletedBeforeTheMorningRunSendsNothing() {
        subscribe(970L);
        insertEvent(998878L, "Short-lived event");
        notificationService.record("EVENT", "CREATED", 998878L, "Short-lived event", "/events",
                System.currentTimeMillis() + 86_400_000L, true, 971L);
        em.createNativeQuery("DELETE FROM \"EVENT\" WHERE id = 998878").executeUpdate();
        notificationService.record("EVENT", "DELETED", 998878L, "Short-lived event", "/events",
                System.currentTimeMillis() + 86_400_000L, false, 971L);
        em.flush();

        makeMorningRunDue();
        scheduler.deliverEmails();
        scheduler.deliverEmails();
        em.flush();

        assertEquals(0, mailbox.getMailsSentTo("mail-subscriber@test.local").size());
        assertEquals(0L, receiptsOf(970L));
    }

    private void subscribe(Long userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUser(em.find(User.class, userId));
        preference.setResourceType("EVENT");
        preference.setEmailEnabled(true);
        preference.setWebhookEnabled(false);
        preference.setUpdatedAt(System.currentTimeMillis());
        em.persist(preference);
        em.flush();
    }

    private void insertEvent(Long id, String title) {
        em.createNativeQuery("DELETE FROM \"EVENT\" WHERE id = :id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("INSERT INTO \"EVENT\" (id,title,description,location,event_date,creator_id) "
                + "VALUES (:id,:title,'Desc','Location',DATEADD('DAY', 2, CURRENT_TIMESTAMP),971)")
                .setParameter("id", id).setParameter("title", title).executeUpdate();
    }

    private void makeMorningRunDue() {
        em.createQuery("UPDATE NotificationItem i SET i.emailFanoutAt = :due WHERE i.emailFanoutAt IS NOT NULL")
                .setParameter("due", System.currentTimeMillis() - 1).executeUpdate();
        em.clear();
    }

    private long receiptsOf(Long userId) {
        return em.createQuery("SELECT COUNT(r) FROM NotificationReceipt r WHERE r.user.id = :id", Long.class)
                .setParameter("id", userId).getSingleResult();
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
