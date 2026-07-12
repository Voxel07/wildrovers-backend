package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class SecretResourceTest {

    @Test void verifyMissing() {
        given().contentType(ContentType.JSON).body("{}").post("/secrets/verify").then().statusCode(400);
    }
    @Test void verifyInvalid() {
        given().contentType(ContentType.JSON).body("{\"email\":\"x@x.com\",\"code\":\"bad\"}").post("/secrets/verify").then().statusCode(400);
    }
    @Test void autoVerifyRequiresAdmin() {
        given().queryParam("username","nonexistent").get("/secrets/auto-verify").then().statusCode(401);
    }
    @Test void resetMissing() {
        given().contentType(ContentType.JSON).body("{}").post("/secrets/reset-request").then().statusCode(400);
    }
    @Test void resetValid() {
        given().contentType(ContentType.JSON).body("{\"email\":\"besucher@test.local\"}").post("/secrets/reset-request").then().statusCode(200);
    }
    @Test void resetUnknown() {
        given().contentType(ContentType.JSON).body("{\"email\":\"unknown@test.local\"}").post("/secrets/reset-request").then().statusCode(200);
    }
    @Test void resetPwMissing() {
        given().contentType(ContentType.JSON).body("{}").post("/secrets/reset-password").then().statusCode(400);
    }
    @Test void resetPwInvalid() {
        given().contentType(ContentType.JSON).body("{\"token\":\"bad\",\"password\":\"newpass123\"}").post("/secrets/reset-password").then().statusCode(400);
    }
}
