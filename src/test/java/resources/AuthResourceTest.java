package resources;

import helper.AccountSnapshotCache;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Browser sessions in HttpOnly cookies: login, refresh rotation, reuse detection, CSRF, logout. */
@QuarkusTest
class AuthResourceTest {
    private static final String CSRF = "X-Requested-With";

    @Inject EntityManager em;
    @Inject AccountSnapshotCache accountSnapshots;
    @Inject @CacheName("team-members") Cache teamMembers;

    @BeforeEach
    @Transactional
    void setup() {
        em.createNativeQuery("DELETE FROM AUTH_SESSION").executeUpdate();
        em.createNativeQuery("DELETE FROM \"SECRET\" WHERE user_id BETWEEN 960 AND 969").executeUpdate();
        em.createNativeQuery("DELETE FROM \"USER\" WHERE id BETWEEN 960 AND 969").executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,lastLoginIp,lastLoginCountry) VALUES "
                + "(960,'auth-member@test.local','authMember','test1234','Auth','Member','Frischling',true,0,false,false,'203.0.113.7','Germany'),"
                + "(961,'auth-admin@test.local','authAdmin','test1234','Auth','Admin','Admin',true,0,true,false,null,null),"
                + "(962,'auth-other@test.local','authOther','test1234','Auth','Other','Besucher',true,0,false,false,null,null)")
                .executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,oidcIssuer,oidcSubject) VALUES "
                + "(963,'auth-oidc@test.local','authOidc','test1234','Auth','Oidc','Mitglied',true,0,false,false,'https://idp.test/','sub-963')")
                .executeUpdate();
        String hash = BcryptUtil.bcryptHash("test1234");
        em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES "
                + "(960,:p,true,'a1',960),(961,:p,true,'a2',961),(962,:p,true,'a3',962),(963,:p,true,'a4',963)")
                .setParameter("p", hash)
                .executeUpdate();
        accountSnapshots.invalidateAll();
        teamMembers.invalidateAll().await().indefinitely();
    }

    /** Other test classes expect their own fixture in the shared team-members cache. */
    @AfterEach
    @Transactional
    void cleanup() {
        em.createNativeQuery("DELETE FROM AUTH_SESSION").executeUpdate();
        em.createNativeQuery("DELETE FROM \"SECRET\" WHERE user_id BETWEEN 960 AND 969").executeUpdate();
        em.createNativeQuery("DELETE FROM \"USER\" WHERE id BETWEEN 960 AND 969").executeUpdate();
        accountSnapshots.invalidateAll();
        teamMembers.invalidateAll().await().indefinitely();
    }

    private Response login(String user) {
        return given().contentType(ContentType.JSON).header(CSRF, "test")
                .body("{\"userName\":\"" + user + "\",\"password\":\"test1234\"}")
                .post("/auth/login");
    }

    @Test
    void loginSetsHttpOnlyCookiesAndReturnsNoToken() {
        Response response = login("authMember");
        response.then().statusCode(200)
                .body("authenticated", is(true))
                .body("userName", equalTo("authMember"))
                .body("role", equalTo("Frischling"))
                .body("JWT", nullValue());

        Cookie access = response.getDetailedCookie("wr_at");
        Cookie refresh = response.getDetailedCookie("wr_rt");
        assertNotNull(access);
        assertNotNull(refresh);
        assertTrue(access.isHttpOnly());
        assertTrue(refresh.isHttpOnly());
        assertEquals("/", access.getPath());
        assertEquals("/auth", refresh.getPath());
        assertEquals("Strict", access.getSameSite());
        assertNotEquals(access.getValue(), refresh.getValue());
        // The refresh cookie is opaque, not a JWT.
        assertFalse(refresh.getValue().contains("."));
    }

    @Test
    void accessCookieAuthenticatesRequests() {
        String access = login("authMember").getCookie("wr_at");
        given().cookie("wr_at", access).get("/auth/session").then().statusCode(200)
                .body("authenticated", is(true))
                .body("userId", is(960));
        given().cookie("wr_at", access).get("/user/me").then().statusCode(200)
                .body("userName", equalTo("authMember"));
    }

    @Test
    void anonymousVisitorsHaveNoSessionWithoutErrors() {
        given().get("/auth/session").then().statusCode(200).body("authenticated", is(false));
        given().header(CSRF, "test").post("/auth/refresh").then().statusCode(200).body("authenticated", is(false));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() {
        String first = login("authMember").getCookie("wr_rt");

        Response rotated = given().header(CSRF, "test").cookie("wr_rt", first).post("/auth/refresh");
        rotated.then().statusCode(200).body("authenticated", is(true));
        String second = rotated.getCookie("wr_rt");
        assertNotNull(second);
        assertNotEquals(first, second);
        assertNotNull(rotated.getCookie("wr_at"));

        // A parallel tab presenting the just-replaced token gets an access token, no new refresh cookie.
        Response grace = given().header(CSRF, "test").cookie("wr_rt", first).post("/auth/refresh");
        grace.then().statusCode(200);
        assertNull(grace.getCookie("wr_rt"));

        // Outside the grace window the old token counts as stolen and ends the whole session.
        backdateRotation();
        given().header(CSRF, "test").cookie("wr_rt", first).post("/auth/refresh").then().statusCode(401);
        given().header(CSRF, "test").cookie("wr_rt", second).post("/auth/refresh").then().statusCode(401);
    }

    @Transactional
    void backdateRotation() {
        em.createQuery("UPDATE AuthSession s SET s.rotatedAt = 0").executeUpdate();
    }

    @Test
    void logoutEndsSessionAndClearsCookies() {
        String refresh = login("authMember").getCookie("wr_rt");
        Response logout = given().header(CSRF, "test").cookie("wr_rt", refresh).post("/auth/logout");
        logout.then().statusCode(204);
        assertEquals(0, logout.getDetailedCookie("wr_rt").getMaxAge());
        assertEquals(0, logout.getDetailedCookie("wr_at").getMaxAge());
        given().header(CSRF, "test").cookie("wr_rt", refresh).post("/auth/refresh").then().statusCode(401);
    }

    @Test
    void blockedAccountCannotRefresh() {
        String refresh = login("authMember").getCookie("wr_rt");
        block(960);
        given().header(CSRF, "test").cookie("wr_rt", refresh).post("/auth/refresh").then()
                .statusCode(403)
                .header("X-Account-Status", equalTo("denied"));
    }

    @Transactional
    void block(long userId) {
        em.createNativeQuery("UPDATE \"USER\" SET isBlocked = true WHERE id = " + userId).executeUpdate();
        accountSnapshots.invalidate(userId);
    }

    @Test
    void cookieRequestsWithoutCsrfHeaderAreRejected() {
        String access = login("authMember").getCookie("wr_at");
        given().contentType(ContentType.JSON).cookie("wr_at", access)
                .body("{\"phrase\":\"Hi\"}")
                .post("/user/me/profile").then().statusCode(403);
        given().contentType(ContentType.JSON).cookie("wr_at", access).header(CSRF, "test")
                .header("Origin", "https://evil.example")
                .body("{\"phrase\":\"Hi\"}")
                .post("/user/me/profile").then().statusCode(403);
        given().contentType(ContentType.JSON).cookie("wr_at", access).header(CSRF, "test")
                .body("{\"phrase\":\"Hi\"}")
                .post("/user/me/profile").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"userName\":\"authMember\",\"password\":\"test1234\"}")
                .post("/auth/login").then().statusCode(403);
    }

    @Test
    void teamMembersDoNotSeeContactOrLoginData() {
        String access = login("authMember").getCookie("wr_at");
        given().cookie("wr_at", access).get("/user/members").then().statusCode(200)
                .body("find { it.id == 960 }.firstName", equalTo("Auth"))
                .body("find { it.id == 960 }.email", nullValue())
                .body("find { it.id == 960 }.lastLoginIp", nullValue())
                .body("find { it.id == 960 }.lastLoginCountry", nullValue())
                .body("find { it.id == 960 }.isBlocked", nullValue());
    }

    @Test
    void userUpdateConflictsAreReportedAsErrors() {
        String access = login("authAdmin").getCookie("wr_at");
        given().contentType(ContentType.JSON).cookie("wr_at", access).header(CSRF, "test")
                .body("{\"id\":962,\"userName\":\"authMember\",\"email\":\"auth-other@test.local\",\"role\":\"Besucher\",\"isActive\":true}")
                .post("/user").then().statusCode(409)
                .body("status", equalTo("error"))
                .body("message", equalTo("Username bereits vergeben"));

        given().contentType(ContentType.JSON).cookie("wr_at", access).header(CSRF, "test")
                .body("{\"id\":963,\"userName\":\"authOidc\",\"email\":\"auth-oidc@test.local\",\"role\":\"Vorstand\",\"isActive\":true}")
                .post("/user").then().statusCode(409);

        given().contentType(ContentType.JSON).cookie("wr_at", access).header(CSRF, "test")
                .body("{\"id\":962,\"userName\":\"authOther\",\"email\":\"auth-other@test.local\",\"role\":\"Mitglied\",\"isActive\":true}")
                .post("/user").then().statusCode(200)
                .body("status", equalTo("success"));
    }

    @Test
    void roleChangeTakesEffectWithoutWaitingForTheCache() {
        String admin = login("authAdmin").getCookie("wr_at");
        String member = login("authOther").getCookie("wr_at");
        given().cookie("wr_at", member).get("/auth/session").then().body("role", equalTo("Besucher"));
        given().contentType(ContentType.JSON).cookie("wr_at", admin).header(CSRF, "test")
                .body("{\"id\":962,\"userName\":\"authOther\",\"email\":\"auth-other@test.local\",\"role\":\"Mitglied\",\"isActive\":true}")
                .post("/user").then().statusCode(200);
        given().cookie("wr_at", member).get("/auth/session").then().body("role", equalTo("Mitglied"));
    }

    @Test
    void errorsHaveOneJsonShape() {
        given().contentType(ContentType.JSON).header(CSRF, "test")
                .body("{\"userName\":\"authMember\",\"password\":\"wrong\"}")
                .post("/auth/login").then().statusCode(401)
                .contentType(ContentType.JSON)
                .body("status", equalTo("error"))
                .body("code", equalTo("UNAUTHORIZED"))
                .body("message", notNullValue());
    }
}
