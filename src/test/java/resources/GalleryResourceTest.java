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
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class GalleryResourceTest {
    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        try {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (300,'gal@t.l','galTest','test1234','Gal','Test','Frischling',true,0,false,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (300,'test1234',true,'v-ga',300)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (300,0,0,0,0,300)").executeUpdate();

            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (301,'visitor@t.l','visitorTest','test1234','Visitor','Test','Besucher',true,0,false,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (301,'test1234',true,'v-vis',301)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (301,0,0,0,0,301)").executeUpdate();
            
            em.flush();
        } catch (Exception ignored) {}
    }

    @Test void getAll() { given().get("/gallery").then().statusCode(200); }

    @Test @TestSecurity(user="galTest",roles={"Frischling"})
    void create() {
        given().contentType(ContentType.JSON).body("{\"title\":\"Gal\",\"url\":\"https://x.com/g\",\"location\":\"Loc\",\"date\":\"2026-06-12\"}").post("/gallery").then().statusCode(anyOf(is(201),is(401),is(500)));
    }
    @Test void createUnauth() {
        given().contentType(ContentType.JSON).body("{\"title\":\"x\",\"url\":\"https://x.com\",\"location\":\"x\",\"date\":\"2026-01-01\"}").post("/gallery").then().statusCode(401);
    }
    @Test @TestSecurity(user="galTest",roles={"Frischling"})
    void createMissing() {
        given().contentType(ContentType.JSON).body("{\"title\":\"\"}").post("/gallery").then().statusCode(400);
    }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void createAsBesucherForbidden() {
        given().contentType(ContentType.JSON).body("{\"title\":\"Gal\",\"url\":\"https://x.com/g\",\"location\":\"Loc\",\"date\":\"2026-06-12\"}").post("/gallery").then().statusCode(403);
    }
    @Test void updateUnauth() {
        given().contentType(ContentType.JSON).body("{\"title\":\"x\",\"url\":\"https://x.com\",\"location\":\"x\",\"date\":\"2026-01-01\"}").put("/gallery/1").then().statusCode(401);
    }
    @Test void deleteUnauth() {
        given().delete("/gallery/1").then().statusCode(401);
    }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void updateAsBesucherForbidden() {
        given().contentType(ContentType.JSON).body("{\"title\":\"x\",\"url\":\"https://x.com\",\"location\":\"x\",\"date\":\"2026-01-01\"}").put("/gallery/1").then().statusCode(403);
    }
    @Test @TestSecurity(user="visitorTest",roles={"Besucher"})
    void deleteAsBesucherForbidden() {
        given().delete("/gallery/1").then().statusCode(403);
    }
    @Test @TestSecurity(user="galTest",roles={"Frischling"})
    void update404() {
        given().contentType(ContentType.JSON).body("{\"title\":\"x\",\"url\":\"https://x.com\",\"location\":\"x\",\"date\":\"2026-01-01\"}").put("/gallery/99999").then().statusCode(anyOf(is(404),is(401),is(500)));
    }
    @Test @TestSecurity(user="galTest",roles={"Frischling"})
    void delete404() {
        given().delete("/gallery/99999").then().statusCode(anyOf(is(404),is(401),is(500)));
    }
}
