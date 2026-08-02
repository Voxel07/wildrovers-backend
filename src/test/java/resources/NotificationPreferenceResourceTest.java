package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

@QuarkusTest
class NotificationPreferenceResourceTest {
    @Inject EntityManager em;
    @Inject tools.NotificationService notificationService;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM NotificationReceipt").executeUpdate();
        em.createQuery("DELETE FROM NotificationItem").executeUpdate();
        em.createQuery("DELETE FROM NotificationPreference p WHERE p.user.id = 950").executeUpdate();
        em.createQuery("DELETE FROM UserWebhook w WHERE w.user.id = 950").executeUpdate();
        if (em.find(model.User.class, 950L) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (950,'notify@test.local','notifyTest','test1234','Notify','Test','Mitglied',true,0,false,false)").executeUpdate();
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
}
