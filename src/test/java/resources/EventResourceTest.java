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
            return Map.of("google.calendar.credentials", "test-disabled");
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

    @Test
    void forumPostTitleContainsEventDateAndTime() {
        model.Event event = new model.Event();
        event.setTitle("Test Event");
        event.setEventDate(LocalDateTime.of(2026, 12, 25, 18, 0));

        assertEquals("Event: Test Event (25.12.2026, 18:00 Uhr)",
                EventResource.formatEventTitleForPost(event));
    }
}
