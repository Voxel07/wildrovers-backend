package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for rate limiting on various endpoints.
 * Rate limits are enforced by {@link helper.RateLimitFilter} using {@link tools.RateLimiter}.
 */
@QuarkusTest
public class RateLimitTest {

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        try {
            em.createNativeQuery("DELETE FROM \"SECRET\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"ACTVITY_FORUM\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"FORUM_CATEGORY\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"EVENT\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"GALLERY\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"USER\"").executeUpdate();
            em.flush();

            // Insert test users
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,yearlyFeePaid) VALUES (100,'besucher@test.local','testBesucher','test1234','Besucher','Test','Besucher',true,0,false,false,false),(104,'admin@test.local','testAdmin','test1234','Admin','Test','Admin',true,0,true,false,true)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (100,'test1234',true,'v-b',100),(104,'test1234',true,'v-a',104)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (100,0,0,0,0,100),(104,0,0,0,0,104)").executeUpdate();
            em.flush();
        } catch (Exception ignored) {}
    }

    // ── Signup rate limit (3 per minute) ──

    @Test
    void testSignupRateLimit() {
        // Limit is 3 per minute
        // First 3 calls should return 201 (Created) or 406 (duplicate), but not 429
        for (int i = 0; i < 3; i++) {
            given()
                .contentType(ContentType.JSON)
                .body("{\"userName\":\"testUserRate" + i + "\",\"email\":\"testrate" + i + "@test.local\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}")
                .put("/user")
                .then()
                .statusCode(anyOf(is(201), is(406)));
        }

        // 4th call must be rate-limited with 429
        given()
            .contentType(ContentType.JSON)
            .body("{\"userName\":\"testUserRate3\",\"email\":\"testrate3@test.local\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}")
            .put("/user")
            .then()
            .statusCode(429);
    }

    // ── Login rate limit (10 per minute) ──

    @Test
    void testLoginRateLimit() {
        // 10 login attempts allowed; 11th gets 429
        for (int i = 0; i < 10; i++) {
            given()
                .contentType(ContentType.JSON)
                .body("{\"userName\":\"testBesucher\",\"password\":\"wrong\"}")
                .post("/user/login")
                .then()
                .statusCode(anyOf(is(200), is(401)));
        }
        given()
            .contentType(ContentType.JSON)
            .body("{\"userName\":\"testBesucher\",\"password\":\"wrong\"}")
            .post("/user/login")
            .then()
            .statusCode(429);
    }

    // ── Password reset rate limit (3 per 5 min) ──

    @Test
    void testPasswordResetRateLimit() {
        for (int i = 0; i < 3; i++) {
            given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"besucher@test.local\"}")
                .post("/secrets/reset-request")
                .then()
                .statusCode(200);
        }
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"besucher@test.local\"}")
            .post("/secrets/reset-request")
            .then()
            .statusCode(429);
    }

    // ── Forum read rate limit (60 per minute) ──

    @Test
    void testForumReadNotRateLimitedUnderLimit() {
        // 5 reads should all succeed
        for (int i = 0; i < 5; i++) {
            given()
                .get("/forum/category")
                .then()
                .statusCode(200);
        }
    }

    // ── Public events GET should succeed ──

    @Test
    void testEventsReadAvailable() {
        given()
            .get("/event")
            .then()
            .statusCode(200);
    }

    // ── Public gallery GET should succeed ──

    @Test
    void testGalleryReadAvailable() {
        given()
            .get("/gallery")
            .then()
            .statusCode(200);
    }
}
