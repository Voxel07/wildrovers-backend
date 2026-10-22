package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(EventResourceTest.Profile.class)
class EventResourceTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Never write to the real Google Calendar from tests, even when
            // GOOGLE_CALENDAR_CREDENTIALS is set in the developer environment.
            // A non-empty value that is not valid JSON makes GoogleCalendarService
            // skip the sync (empty strings fail Quarkus config validation).
            return Map.of("google.calendar.credentials", "test-disabled",
                    "app.events.forum-topic-id", "250");
        }
    }

    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        try {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (200,'evtest@t.l','evTest','test1234','Ev','Test','Frischling',true,0,false,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (200,'test1234',true,'v-ev',200)").executeUpdate();

            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (201,'visitor@t.l','visitorTest','test1234','Visitor','Test','Besucher',true,0,false,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (201,'test1234',true,'v-vis',201)").executeUpdate();

            em.flush();
        } catch (Exception ignored) {}
    }

    /** Forum topic that receives the event posts (app.events.forum-topic-id=250 above). No creator:
     * JUnit does not order the @BeforeEach methods, so the users may not exist yet. */
    @BeforeEach @Transactional
    void setupEventTopic() {
        Number existing = (Number) em.createNativeQuery("SELECT COUNT(*) FROM \"FORUM_TOPIC\" WHERE id = 250").getSingleResult();
        if (existing.intValue() > 0) return;
        em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (250,'EventCat',0,1,50,'Besucher',NULL)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (250,'Events',0,0,0,NULL,250)").executeUpdate();
    }

    @Test void getAll() { given().get("/event").then().statusCode(200); }
    @Test void upcoming() { given().get("/event/upcoming").then().statusCode(200); }
    @Test void missingEventByPostIsEmpty() {
        given().get("/event/by-post/99999").then().statusCode(204);
    }

    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void createEvent() {
        // Google Calendar sync is disabled in the test profile (empty credentials),
        // so this creates the event only in the in-memory H2 database.
        given().contentType(ContentType.JSON).body("{\"title\":\"Test Event\",\"eventDate\":\"2026-12-25T18:00:00\",\"location\":\"Test Loc\"}").post("/event").then().statusCode(201);
    }
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void createMissing() {
        given().contentType(ContentType.JSON).body("{\"title\":\"\"}").post("/event").then().statusCode(400);
    }
    @Test void createUnauth() {
        given().contentType(ContentType.JSON).body("{\"title\":\"x\",\"eventDate\":\"2026-01-01T00:00:00\",\"location\":\"x\"}").post("/event").then().statusCode(401);
    }
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void update404() {
        given().contentType(ContentType.JSON).body("{\"id\":99999,\"title\":\"x\",\"eventDate\":\"2026-01-01T00:00:00\",\"location\":\"x\"}").put("/event").then().statusCode(anyOf(is(404),is(500)));
    }
    @Test void updateUnauth() {
        given().contentType(ContentType.JSON).body("{\"id\":1,\"title\":\"x\",\"eventDate\":\"2026-01-01T00:00:00\",\"location\":\"x\"}").put("/event").then().statusCode(401);
    }
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void delete404() { given().delete("/event/99999").then().statusCode(anyOf(is(404),is(500))); }
    @Test void deleteUnauth() { given().delete("/event/1").then().statusCode(401); }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void attendance404() { given().contentType(ContentType.JSON).body("{\"status\":\"YES\"}").post("/event/99999/attendance").then().statusCode(anyOf(is(404),is(500))); }
    @Test void attendanceUnauth() { given().contentType(ContentType.JSON).body("{\"status\":\"YES\"}").post("/event/1/attendance").then().statusCode(401); }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void attendanceInvalid() { given().contentType(ContentType.JSON).body("{\"status\":\"INVALID\"}").post("/event/99999/attendance").then().statusCode(anyOf(is(400),is(404),is(500))); }

    @Transactional
    Long createGuestEvent() {
        model.Event event = new model.Event();
        event.setTitle("Guest participation");
        event.setLocation("Home");
        event.setEventDate(java.time.LocalDateTime.of(2035, 1, 1, 12, 0));
        em.persist(event);
        return event.getId();
    }

    @Test @TestSecurity(user="visitorTest", roles={"Besucher"})
    void visitorsCanRsvpAsGuestsWithoutReceivingTeamDetails() {
        Long eventId = createGuestEvent();
        given().contentType(ContentType.JSON).body("{\"status\":\"YES\"}")
                .post("/event/" + eventId + "/attendance").then().statusCode(200)
                .body("attendances.size()", is(1)).body("attendances[0].status", is("YES"))
                .body("attendances[0].userName", is("Anonym"))
                .body("attendances[0].userId", nullValue()).body("nonRespondents.size()", is(0));
        given().contentType(ContentType.JSON).body("{}")
                .post("/event/" + eventId + "/attendance").then().statusCode(400);
    }

    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void createAsBesucherForbidden() {
        given().contentType(ContentType.JSON).body("{\"title\":\"Test Event\",\"eventDate\":\"2026-12-25T18:00:00\",\"location\":\"Test Loc\"}").post("/event").then().statusCode(403);
    }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void updateAsBesucherForbidden() {
        given().contentType(ContentType.JSON).body("{\"id\":1,\"title\":\"x\",\"eventDate\":\"2026-01-01T00:00:00\",\"location\":\"x\"}").put("/event").then().statusCode(403);
    }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void deleteAsBesucherForbidden() {
        given().delete("/event/1").then().statusCode(403);
    }

    // ── Event ↔ forum post (3.3) ──
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void eventPostFollowsEventEditsAndDeletion() {
        Number eventId = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Linked Event\",\"eventDate\":\"2031-05-01T18:00:00\",\"location\":\"Halle\"}")
                .post("/event").then().statusCode(201)
                .body("forumPostId", notNullValue())
                .extract().path("id");
        Number postId = given().queryParam("year", 2031).get("/event").then().statusCode(200)
                .extract().path("find { it.id == " + eventId + " }.forumPostId");
        given().get("/event/by-post/" + postId).then().statusCode(200).body("id", is(eventId.intValue()));
        given().queryParam("post", postId).get("/forum/post").then().statusCode(200)
                .body("[0].title", is("Event: Linked Event (01.05.2031, 18:00 Uhr)"));

        given().contentType(ContentType.JSON)
                .body("{\"id\":" + eventId + ",\"title\":\"Renamed Event\",\"eventDate\":\"2031-05-02T19:30:00\",\"location\":\"Platz\"}")
                .put("/event").then().statusCode(200);
        given().queryParam("post", postId).get("/forum/post").then().statusCode(200)
                .body("[0].title", is("Event: Renamed Event (02.05.2031, 19:30 Uhr)"))
                .body("[0].content", containsString("Platz"));

        given().delete("/event/" + eventId).then().statusCode(200);
        given().queryParam("post", postId).get("/forum/post").then().statusCode(200).body("size()", is(0));
    }

    // ── Validation (3.5) ──
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void endBeforeStartIs400() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Backwards\",\"eventDate\":\"2031-05-01T18:00:00\",\"eventEndDate\":\"2031-05-01T17:00:00\",\"location\":\"x\"}")
                .post("/event").then().statusCode(400);
    }
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void descriptionTooLongAfterSanitisingIs400() {
        // 600 '<' become 600 "&lt;" (2400 characters) after sanitising.
        String description = "<".repeat(600);
        given().contentType(ContentType.JSON)
                .body(Map.of("title", "Long", "eventDate", "2031-06-01T18:00:00", "location", "x", "description", description))
                .post("/event").then().statusCode(400);
    }
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void tooLongTitleIs400() {
        given().contentType(ContentType.JSON)
                .body(Map.of("title", "x".repeat(201), "eventDate", "2031-06-01T18:00:00", "location", "x"))
                .post("/event").then().statusCode(400);
    }

    // ── Year filter (3.6) ──
    @Test @TestSecurity(user="evTest",roles={"Frischling"})
    void yearFilterOnlyReturnsThatYear() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Year A\",\"eventDate\":\"2032-03-01T18:00:00\",\"location\":\"x\"}")
                .post("/event").then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Year B\",\"eventDate\":\"2033-03-01T18:00:00\",\"location\":\"x\"}")
                .post("/event").then().statusCode(201);
        given().queryParam("year", 2032).get("/event").then().statusCode(200)
                .body("title", hasItem("Year A")).body("title", not(hasItem("Year B")));
        given().get("/event/years").then().statusCode(200).body("$", hasItems(2032, 2033));
        given().queryParam("from", "2033-01-01").queryParam("to", "2034-01-01").get("/event").then().statusCode(200)
                .body("title", hasItem("Year B")).body("title", not(hasItem("Year A")));
        given().queryParam("from", "not-a-date").get("/event").then().statusCode(400);
    }

    @Test
    void forumPostTitleContainsEventDateAndTime() {
        model.Event event = new model.Event();
        event.setTitle("Test Event");
        event.setEventDate(LocalDateTime.of(2026, 12, 25, 18, 0));

        assertEquals("Event: Test Event (25.12.2026, 18:00 Uhr)",
                EventResource.formatEventTitleForPost(event));
    }
}
