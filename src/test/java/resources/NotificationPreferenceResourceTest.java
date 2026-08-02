package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class NotificationPreferenceResourceTest {
    @Inject EntityManager em;
    @Inject tools.NotificationService notificationService;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM NotificationReceipt").executeUpdate();
        em.createQuery("DELETE FROM NotificationItem").executeUpdate();
        em.createQuery("DELETE FROM NotificationPreference p WHERE p.user.id IN (950, 951, 952, 953)").executeUpdate();
        em.createQuery("DELETE FROM UserWebhook w WHERE w.user.id IN (950, 951, 952, 953)").executeUpdate();
        if (em.find(model.User.class, 950L) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (950,'notify@test.local','notifyTest','test1234','Notify','Test','Mitglied',true,0,false,false)").executeUpdate();
        }
        if (em.find(model.User.class, 951L) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (951,'notify-admin@test.local','notifyAdmin','test1234','Notify','Admin','Admin',true,0,true,false)").executeUpdate();
        }
        if (em.find(model.User.class, 952L) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (952,'notify-vorstand@test.local','notifyVorstand','test1234','Notify','Vorstand','Vorstand',true,0,true,false)").executeUpdate();
        }
        if (em.find(model.User.class, 953L) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (953,'new-user@test.local','newNotifyUser','test1234','New','User','Besucher',true,0,false,false)").executeUpdate();
        }
    }

    @Test
    void anonymousCannotReadPreferences() {
        given().get("/user/me/notifications").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "notifyTest", roles = "Mitglied")
    void defaultsAreOptInFalse() {
        given().get("/user/me/notifications").then().statusCode(200)
                .body("resources.EVENT.email", equalTo(false))
                .body("resources.EVENT.webhook", equalTo(false))
                .body("resources.FORUM.email", equalTo(false))
                .body("resources.GALLERY.email", equalTo(false))
                .body("resources.SIGNUP", nullValue())
                .body("webhookSecret", nullValue());
    }

    @Test
    @TestSecurity(user = "notifyTest", roles = "Mitglied")
    void savesPerResourceEmailPreferences() {
        String body = """
                {"resources":{
                  "EVENT":{"email":true,"webhook":false},
                  "FORUM":{"email":true,"webhook":false},
                  "GALLERY":{"email":false,"webhook":false}
                }}
                """;
        given().contentType(ContentType.JSON).body(body).put("/user/me/notifications").then().statusCode(200)
                .body("resources.EVENT.email", equalTo(true))
                .body("resources.FORUM.email", equalTo(true))
                .body("resources.GALLERY.email", equalTo(false));
    }

    @Test
    @TestSecurity(user = "notifyTest", roles = "Mitglied")
    void rejectsWebhookSelectionWithoutUrl() {
        String body = "{\"resources\":{\"EVENT\":{\"email\":false,\"webhook\":true}}}";
        given().contentType(ContentType.JSON).body(body).put("/user/me/notifications").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "notifyTest", roles = "Mitglied")
    void rejectsPrivateWebhookTarget() {
        String body = "{\"resources\":{},\"webhookUrl\":\"http://127.0.0.1/internal\"}";
        given().contentType(ContentType.JSON).body(body).put("/user/me/notifications").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "notifyTest", roles = "Mitglied")
    void acknowledgeAndDeleteAreIdempotent() {
        given().post("/user/me/notifications/acknowledge").then().statusCode(200).body("acknowledged", equalTo(0));
        given().delete("/user/me/notifications/webhook").then().statusCode(204);
    }

    @Test
    void deliveryQueriesAreValidWhenQueueIsEmpty() {
        org.junit.jupiter.api.Assertions.assertTrue(notificationService.dueEventBatches("EMAIL").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(notificationService.dueEventBatches("WEBHOOK").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(notificationService.dueDigestBatches("EMAIL").isEmpty());
    }

    @Test
    @Transactional
    void subscribedActorDoesNotReceiveTheirOwnEventNotification() {
        model.notifications.NotificationPreference preference = new model.notifications.NotificationPreference();
        preference.setUser(em.find(model.User.class, 950L));
        preference.setResourceType("EVENT");
        preference.setEmailEnabled(true);
        preference.setWebhookEnabled(false);
        preference.setUpdatedAt(System.currentTimeMillis());
        em.persist(preference);

        notificationService.record("EVENT", "CREATED", 987654L, "Own event", "/events",
                System.currentTimeMillis() + 86_400_000L, true, 950L);
        em.flush();

        Long receiptCount = em.createQuery(
                "SELECT COUNT(r) FROM NotificationReceipt r WHERE r.user.id = :userId AND r.item.entityId = :eventId",
                Long.class)
                .setParameter("userId", 950L)
                .setParameter("eventId", 987654L)
                .getSingleResult();
        assertEquals(0L, receiptCount);
    }

    @Test
    @Transactional
    void forumWebhookIsDueImmediatelyWhileEmailWaitsForDigest() {
        persistPreference(950L, "FORUM", true, true);

        notificationService.record("FORUM", "CREATED", 987655L, "Forum post", "/Forum/Post/987655",
                null, false, 951L);
        em.flush();

        assertEquals(1, notificationService.dueDigestBatches("WEBHOOK").size());
        assertEquals(0, notificationService.dueDigestBatches("EMAIL").size());
    }

    @Test
    @Transactional
    void signupNotificationsOnlyFanOutToOptedInAdminAndVorstand() {
        persistPreference(950L, "SIGNUP", true, false);
        persistPreference(951L, "SIGNUP", true, false);
        persistPreference(952L, "SIGNUP", false, true);

        notificationService.recordSignup(em.find(model.User.class, 953L), "https://wildrovers.example");
        em.flush();

        List<Long> recipientIds = em.createQuery(
                "SELECT r.user.id FROM NotificationReceipt r WHERE r.item.resourceType = 'SIGNUP' ORDER BY r.user.id",
                Long.class).getResultList();
        assertEquals(List.of(951L, 952L), recipientIds);
        assertEquals(1, notificationService.dueDigestBatches("WEBHOOK").size());
        assertEquals(0, notificationService.dueDigestBatches("EMAIL").size());
    }

    @Test
    @TestSecurity(user = "notifyAdmin", roles = "Admin")
    void adminCanOptInToSignupNotifications() {
        String body = "{\"resources\":{\"SIGNUP\":{\"email\":true,\"webhook\":false}}}";
        given().contentType(ContentType.JSON).body(body).put("/user/me/notifications").then().statusCode(200)
                .body("resources.SIGNUP.email", equalTo(true));
    }

    private void persistPreference(Long userId, String resourceType, boolean email, boolean webhook) {
        model.notifications.NotificationPreference preference = new model.notifications.NotificationPreference();
        preference.setUser(em.find(model.User.class, userId));
        preference.setResourceType(resourceType);
        preference.setEmailEnabled(email);
        preference.setWebhookEnabled(webhook);
        preference.setUpdatedAt(System.currentTimeMillis());
        em.persist(preference);
    }
}
