package resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/** Metrics and health live on the separate management port, never on the public API port. */
@QuarkusTest
class ManagementInterfaceTest {

    @Test
    void metricsAndHealthAreNotOnThePublicPort() {
        given().get("/q/metrics").then().statusCode(404);
        given().get("/q/health").then().statusCode(404);
    }
}
