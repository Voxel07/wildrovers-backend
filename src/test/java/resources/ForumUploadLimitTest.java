package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(ForumUploadLimitTest.Profile.class)
@TestSecurity(user = "quotaUser", roles = "Mitglied")
class ForumUploadLimitTest {
    public static class Profile implements QuarkusTestProfile {
        public Map<String, String> getConfigOverrides() {
            return Map.of("forum.images.max-user-daily-bytes", "1");
        }
    }

    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        if (em.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = 9200", Long.class).getSingleResult() > 0) return;
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (9200,'quota@t.l','quotaUser','test1234','Quota','User','Mitglied',true,0,false,false)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (9200,'test1234',true,'quota',9200)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_CATEGORY (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (9200,'QuotaCategory',0,1,0,'Besucher',9200)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_TOPIC (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (9200,'QuotaTopic',0,0,0,9200,9200)").executeUpdate();
    }

    private byte[] image() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test void editorUploadReturnsTheQuotaError() throws Exception {
        given().multiPart("file", "picture.png", image(), "image/png")
                .post("/forum/img/upload").then().statusCode(429);
    }

    @Test void embeddedImageCannotBypassQuotaAndRejectedPostIsRolledBack() throws Exception {
        String content = "<img src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(image()) + "\">";
        given().contentType(ContentType.JSON).queryParam("topic", 9200)
                .body(Map.of("title", "Rejected by quota", "content", content))
                .put("/forum/post").then().statusCode(429);
        given().queryParam("topic", 9200).get("/forum/post").then().statusCode(200).body("size()", is(0));
        given().queryParam("topicId", 9200).get("/forum/topic").then().statusCode(200).body("[0].postCount", is(0));
    }
}
