package helper;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
class LocalJwtAuthMechanismTest {
    @Inject EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM Secret s WHERE s.user.id = 980").executeUpdate();
        em.createQuery("DELETE FROM User u WHERE u.id = 980").executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) "
                + "VALUES (980,'local-login@test.local','localLoginUser','OIDC_DUMMY','Local','Login','Besucher',true,0,false,false)")
                .executeUpdate();
        em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (980,:password,true,'verified',980)")
                .setParameter("password", BcryptUtil.bcryptHash("test1234"))
                .executeUpdate();
    }

    @Test
    void locallyIssuedJwtCanResolveNewDatabaseUser() {
        String token = given()
                .contentType(ContentType.JSON)
                .body("{\"userName\":\"localLoginUser\",\"password\":\"test1234\"}")
                .post("/user/login")
                .then().statusCode(200)
                .extract().path("JWT");

        given()
                .header("Authorization", "Bearer " + token)
                .get("/user/me")
                .then().statusCode(200)
                .body("userName", equalTo("localLoginUser"));
    }
}
