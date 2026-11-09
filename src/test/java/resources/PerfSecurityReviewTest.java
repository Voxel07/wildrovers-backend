package resources;

import helper.AccountSnapshotCache;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.User;
import orm.Secrets.SecretOrm;
import orm.UserOrm;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.EmailQuotaService;
import tools.HtmlSanitizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixes from REVIEW-PERF-SECURITY.md (2026-11-09). */
@QuarkusTest
class PerfSecurityReviewTest {
    private static final String CSRF = "X-Requested-With";
    private static final Pattern CODE = Pattern.compile(">([0-9]{6})<");

    @Inject EntityManager em;
    @Inject MockMailbox mailbox;
    @Inject UserOrm userOrm;
    @Inject SecretOrm secretOrm;
    @Inject EmailQuotaService quota;
    @Inject HtmlSanitizer sanitizer;
    @Inject AccountSnapshotCache accountSnapshots;
    @Inject @CacheName("team-members") Cache teamMembers;

    @ConfigProperty(name = "user.photos.upload-dir") String photoDir;
    @ConfigProperty(name = "user.backgrounds.upload-dir") String backgroundDir;

    @BeforeEach
    @Transactional
    void setup() {
        mailbox.clear();
        clear();
        String hash = BcryptUtil.bcryptHash("test1234");
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,birthday,lastLoginIp,lastLoginCountry) VALUES "
                + "(980,'review-local@test.local','reviewLocal','test1234','Review','Local','Besucher',true,0,false,false,'1990-01-01','203.0.113.9','Germany'),"
                + "(981,'review-board@test.local','reviewBoard','test1234','Review','Board','Besucher',true,0,false,false,null,null,null),"
                + "(982,'review-unverified@test.local','reviewUnverified','test1234','Review','Unverified','Besucher',true,0,false,false,null,null,null)")
                .executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,oidcIssuer,oidcSubject) VALUES "
                + "(983,'review-oidc@test.local','reviewOidc','test1234','Review','Oidc','Mitglied',true,0,false,false,'https://idp.test/','sub-983')")
                .executeUpdate();
        em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,verificationTimestamp,user_id) VALUES "
                + "(980,:p,true,'111111',0,980),(981,:p,true,'222222',0,981),(982,:p,false,'123456',:now,982),(983,:p,true,'444444',0,983)")
                .setParameter("p", hash)
                .setParameter("now", System.currentTimeMillis())
                .executeUpdate();
        em.createNativeQuery("INSERT INTO \"PHONE\" (id,number,type,user_id) VALUES (980,'+49 123 980','mobil',980)").executeUpdate();
        accountSnapshots.invalidateAll();
        teamMembers.invalidateAll().await().indefinitely();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        clear();
        accountSnapshots.invalidateAll();
        teamMembers.invalidateAll().await().indefinitely();
    }

    private void clear() {
        em.createNativeQuery("DELETE FROM AUTH_SESSION WHERE user_id BETWEEN 980 AND 989").executeUpdate();
        em.createNativeQuery("DELETE FROM \"PHONE\" WHERE user_id BETWEEN 980 AND 989").executeUpdate();
        em.createNativeQuery("DELETE FROM \"SECRET\" WHERE user_id BETWEEN 980 AND 989").executeUpdate();
        em.createNativeQuery("DELETE FROM \"USER\" WHERE id BETWEEN 980 AND 989 OR userName = 'reviewNewName'").executeUpdate();
        em.createNativeQuery("DELETE FROM \"EMAIL_SEND_LOG\"").executeUpdate();
    }

    private Response login(String user, String password) {
        return given().contentType(ContentType.JSON).header(CSRF, "test")
                .body("{\"userName\":\"" + user + "\",\"password\":\"" + password + "\"}")
                .post("/auth/login");
    }

    private static List<Mail> awaitMails(MockMailbox mailbox, String recipient) {
        for (int i = 0; i < 50 && mailbox.getMailsSentTo(recipient).isEmpty(); i++) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return mailbox.getMailsSentTo(recipient);
    }

    @Transactional
    String emailOf(long id) {
        return em.find(User.class, id).getEmail();
    }

    // ── A-S1: e-mail change needs the code sent to the new address ──────────────

    @Test
    void emailChangeIsStoredOnlyAfterConfirmation() {
        String access = login("reviewLocal", "test1234").then().statusCode(200).extract().cookie("wr_at");

        given().cookie("wr_at", access).header(CSRF, "test").contentType(ContentType.JSON)
                .body("{\"email\":\"not-an-address\"}")
                .post("/user/me/profile").then().statusCode(400);

        given().cookie("wr_at", access).header(CSRF, "test").contentType(ContentType.JSON)
                .body("{\"email\":\"review-new@test.local\"}")
                .post("/user/me/profile").then().statusCode(200)
                .body("email", equalTo("review-local@test.local"))
                .body("pendingEmail", equalTo("review-new@test.local"));
        assertEquals("review-local@test.local", emailOf(980));

        List<Mail> mails = awaitMails(mailbox, "review-new@test.local");
        assertEquals(1, mails.size());
        Matcher code = CODE.matcher(mails.get(0).getHtml());
        assertTrue(code.find(), "mail contains the code");
        String wrong = code.group(1).equals("000000") ? "111111" : "000000";

        given().cookie("wr_at", access).header(CSRF, "test").contentType(ContentType.JSON)
                .body("{\"code\":\"" + wrong + "\"}")
                .post("/user/me/email/confirm").then().statusCode(400);
        assertEquals("review-local@test.local", emailOf(980));

        given().cookie("wr_at", access).header(CSRF, "test").contentType(ContentType.JSON)
                .body("{\"code\":\"" + code.group(1) + "\"}")
                .post("/user/me/email/confirm").then().statusCode(200)
                .body("email", equalTo("review-new@test.local"));
        assertEquals("review-new@test.local", emailOf(980));
    }

    @Test
    void emailChangeCodeIsVoidAfterFiveWrongAttempts() {
        UserOrm.ProfileUpdate update = userOrm.updateUserProfile(980L, null, null, null, null, "review-new@test.local", null);
        assertNotNull(update.code());
        String wrong = update.code().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertNotNull(userOrm.confirmEmailChange(980L, wrong));
        }
        assertNotNull(userOrm.confirmEmailChange(980L, update.code()));
        assertEquals("review-local@test.local", emailOf(980));
    }

    // ── A-S1 / A-S3: OIDC linking ends the local password and its sessions ─────

    @Test
    void oidcLinkingInvalidatesTheLocalPasswordAndSessions() {
        Response before = login("reviewBoard", "test1234").then().statusCode(200).extract().response();
        String refresh = before.getCookie("wr_rt");

        UserOrm.OidcResolution resolution = userOrm.resolveOidcUser(new UserOrm.OidcProfile(
                "https://idp.test/", "sub-981", "board", "review-board@test.local", true, "Board", "Member"), "Vorstand");
        assertNotNull(resolution.user());
        assertEquals(981L, resolution.user().getId());

        // The old password no longer works and the session opened with it is gone.
        login("reviewBoard", "test1234").then().statusCode(401);
        given().header(CSRF, "test").cookie("wr_rt", refresh).post("/auth/refresh").then().statusCode(401);
    }

    @Test
    void localLoginAndPasswordResetAreRefusedForOidcAccounts() {
        login("reviewOidc", "test1234").then().statusCode(401).body("code", equalTo("OIDC_ACCOUNT"));
        assertNull(secretOrm.createResetToken("review-oidc@test.local"));
        assertNotNull(secretOrm.createResetToken("review-local@test.local"));
    }

    // ── A-S5 / A-S8: verification codes ──────────────────────────────────────────

    @Test
    void verificationCodeIsVoidAfterFiveWrongAttemptsAndAnswersAreUniform() {
        String unknown = secretOrm.verifyUser("nobody@test.local", "123456");
        for (int i = 0; i < 5; i++) {
            assertEquals(unknown, secretOrm.verifyUser("review-unverified@test.local", "999999"));
        }
        assertEquals(SecretOrm.VERIFICATION_FAILED, secretOrm.verifyUser("review-unverified@test.local", "123456"));
        assertEquals(SecretOrm.VERIFICATION_FAILED, unknown);
    }

    @Test
    void correctVerificationCodeVerifies() {
        assertEquals(SecretOrm.VERIFIED, secretOrm.verifyUser("review-unverified@test.local", "123456"));
        // A repeated submit after success is not reported as an error.
        assertEquals(SecretOrm.VERIFIED, secretOrm.verifyUser("review-unverified@test.local", "123456"));
    }

    // ── A-S8: sign-up does not reveal registered addresses ──────────────────────

    @Test
    void signupWithRegisteredAddressAnswersLikeANewSignup() {
        String body = "{\"userName\":\"reviewNewName\",\"email\":\"review-local@test.local\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}";
        String answer = given().contentType(ContentType.JSON).header(CSRF, "test").body(body).put("/user")
                .then().statusCode(201).extract().asString();
        assertTrue(answer.contains("Nutzer erfolgreich erstellt"), answer);
        // The owner of the address gets a notice instead of a second account.
        assertEquals(1, awaitMails(mailbox, "review-local@test.local").size());
        assertNull(userOrm.findByUsername("reviewNewName"));
    }

    // ── A-S6: deletion removes personal data and photo files ────────────────────

    @Test
    void softDeleteRemovesPersonalDataAndPhotos() throws Exception {
        Path photo = Path.of(photoDir.replace("${user.home}", System.getProperty("user.home")), "980.jpg");
        Path background = Path.of(backgroundDir.replace("${user.home}", System.getProperty("user.home")), "980_background.jpg");
        Files.createDirectories(photo.getParent());
        Files.createDirectories(background.getParent());
        Files.write(photo, new byte[] { 1 });
        Files.write(background, new byte[] { 1 });
        given().get("/user/photo/980").then().statusCode(200);

        userOrm.deleteUserWithOptions(980L, true, true, false, true, false);

        assertFalse(Files.exists(photo));
        assertFalse(Files.exists(background));
        given().get("/user/photo/980").then().statusCode(404);
        User deleted = userOrm.findById(980L);
        assertNotNull(deleted);
        assertNotEquals("review-local@test.local", deleted.getEmail());
        assertTrue(deleted.getEmail().endsWith("@deleted.invalid"));
        assertNull(deleted.getBirthday());
        assertNull(deleted.getLastLoginIp());
        assertEquals(0L, ((Number) em.createNativeQuery("SELECT COUNT(*) FROM \"PHONE\" WHERE user_id = 980")
                .getSingleResult()).longValue());
    }

    // ── A-S4: mail types have separate hourly shares ────────────────────────────

    @Test
    void signupMailsCannotUseUpThePasswordResetShare() {
        int reserved = 0;
        for (int i = 0; i < 20; i++) {
            if (quota.reserve("flood" + i + "@test.local", EmailQuotaService.TYPE_VERIFICATION) != null) reserved++;
        }
        assertTrue(reserved < 20, "sign-up share is capped");
        assertNotNull(quota.reserve("victim@test.local", EmailQuotaService.TYPE_PASSWORD_RESET));
        assertNotNull(quota.reserve("victim@test.local", EmailQuotaService.TYPE_NOTIFICATION));
    }

    // ── A-S11: auto-verify is a POST ────────────────────────────────────────────

    @Test
    void autoVerifyDoesNotAcceptGet() {
        given().queryParam("username", "reviewUnverified").get("/secrets/auto-verify").then().statusCode(is(405));
    }

    // ── A-S12: posts only embed our own images ──────────────────────────────────

    @Test
    void sanitizerKeepsOnlyForumImages() {
        String remote = sanitizer.sanitize("<p><img src=\"https://tracker.example/pixel.gif\"></p>");
        assertFalse(remote.contains("tracker.example"), remote);
        String own = sanitizer.sanitize("<p><img src=\"/forum/img/12/thumb/a.jpg\" alt=\"x\"></p>");
        assertTrue(own.contains("/forum/img/12/thumb/a.jpg"), own);
        String signed = sanitizer.sanitize("<img src=\"http://localhost:8080/forum/img/12/full/a.jpg?exp=1&amp;sig=abc\">");
        assertTrue(signed.contains("/forum/img/12/full/a.jpg"), signed);
        String javascript = sanitizer.sanitize("<img src=\"javascript:alert(1)\">");
        assertFalse(javascript.contains("javascript"), javascript);
    }
}
