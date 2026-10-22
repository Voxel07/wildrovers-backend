package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "reviewLow", roles = "Admin")
class ReviewLowResourceTest {
    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        em.createNativeQuery("DELETE FROM FORUM_POLL_OPTIONS WHERE poll_id = 9100").executeUpdate();
        em.createNativeQuery("DELETE FROM FORUM_POLLS WHERE id = 9100").executeUpdate();
        em.createNativeQuery("DELETE FROM FORUM_ANSWERS WHERE id = 9100").executeUpdate();
        em.createNativeQuery("DELETE FROM FORUM_POSTS WHERE topic_id = 9100").executeUpdate();
        em.createNativeQuery("DELETE FROM FORUM_TOPIC WHERE id = 9100").executeUpdate();
        em.createNativeQuery("DELETE FROM FORUM_CATEGORY WHERE id BETWEEN 9100 AND 9102").executeUpdate();
        if (em.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = 9100", Long.class).getSingleResult() == 0) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (9100,'review-low@t.l','reviewLow','test1234','Review','Low','Admin',true,0,true,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (9100,'test1234',true,'review-low',9100)").executeUpdate();
        }
        if (em.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = 9101", Long.class).getSingleResult() == 0) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (9101,'review-visitor@t.l','reviewVisitor','test1234','Review','Visitor','Besucher',true,0,false,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (9101,'test1234',true,'review-visitor',9101)").executeUpdate();
        }
        em.createNativeQuery("INSERT INTO FORUM_CATEGORY (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (9100,'ReviewAlpha',0,1,4,'Besucher',9100),(9101,'ReviewBeta',0,0,4,'Besucher',9100),(9102,'ReviewGamma',0,0,100,'Besucher',9100)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_TOPIC (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (9100,'ReviewLowTopic',0,1,0,9100,9100)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_POSTS (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (9100,'Existing title','<p>Text</p>',0,0,0,1,9100,9100)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_ANSWERS (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (9100,'<p>Answer</p>',0,0,0,9100,9100)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_POLLS (id,question,post_id,allow_multiple,anonymous) VALUES (9100,'Test poll',9100,false,false)").executeUpdate();
        em.createNativeQuery("INSERT INTO FORUM_POLL_OPTIONS (id,option_text,votes,poll_id) VALUES (9100,'Option',0,9100)").executeUpdate();
    }

    @Test void duplicateTitlesAreComparedAfterSanitizing() {
        given().contentType(ContentType.JSON).queryParam("topic", 9100)
                .body("{\"title\":\"<b>Existing title</b>\",\"content\":\"text\"}")
                .put("/forum/post").then().statusCode(409);
    }

    @Test void validatesTitlesOnCreateAndUpdate() {
        for (String title : List.of("<b></b>", "x".repeat(256))) {
            given().contentType(ContentType.JSON).queryParam("topic", 9100)
                    .body(java.util.Map.of("title", title, "content", "text"))
                    .put("/forum/post").then().statusCode(400);
            given().contentType(ContentType.JSON).body(java.util.Map.of("id", 9100, "title", title))
                    .post("/forum/post").then().statusCode(400);
        }
        given().queryParam("post", 9100).get("/forum/post").then().body("[0].title", is("Existing title"));
    }

    @Test void answerEditsUseEpochMillis() {
        long before = System.currentTimeMillis();
        given().contentType(ContentType.JSON).body("{\"id\":9100,\"content\":\"<p>Edited</p>\"}")
                .post("/forum/answer").then().statusCode(200);
        Number edited = given().queryParam("post", 9100).get("/forum/answer").then().statusCode(200)
                .extract().jsonPath().get("[0].editDate");
        assertTrue(edited.longValue() >= before);
        assertTrue(edited.longValue() <= System.currentTimeMillis());
    }

    private void assertContiguousCategories() {
        List<Integer> positions = given().get("/forum/category").then().statusCode(200)
                .extract().jsonPath().getList("position", Integer.class);
        assertEquals(java.util.stream.IntStream.range(0, positions.size()).boxed().toList(), positions);
    }

    @Test void movingCategoriesRepairsGapsAndDuplicatePositionsInBothDirections() {
        given().contentType(ContentType.JSON).body("{\"id\":9102,\"category\":\"ReviewGamma\",\"position\":0}")
                .post("/forum/category").then().statusCode(200);
        assertContiguousCategories();
        given().get("/forum/category").then().body("[0].id", is(9102));
        given().contentType(ContentType.JSON).body("{\"id\":9102,\"category\":\"ReviewGamma\",\"position\":999999}")
                .post("/forum/category").then().statusCode(200);
        assertContiguousCategories();
        List<Integer> ids = given().get("/forum/category").jsonPath().getList("id", Integer.class);
        assertEquals(9102, ids.getLast());
    }

    @Test void insertionAndDeletionKeepCategoriesContiguous() {
        given().contentType(ContentType.JSON).body("{\"category\":\"ReviewInserted\",\"position\":999999}")
                .put("/forum/category").then().statusCode(201);
        assertContiguousCategories();
        given().contentType(ContentType.JSON).body("{\"id\":9101}")
                .delete("/forum/category").then().statusCode(200);
        assertContiguousCategories();
    }

    @Test void pollsLoadOnlyForPostDetails() {
        given().queryParam("topic", 9100).get("/forum/post").then().statusCode(200)
                .body("[0].polls.size()", is(0));
        given().queryParam("post", 9100).get("/forum/post").then().statusCode(200)
                .body("[0].polls[0].question", is("Test poll"))
                .body("[0].polls[0].options.size()", is(1));
    }

    @Test void invalidGalleryUrlIsRejectedOnCreateAndUpdate() {
        String body = "{\"title\":\"ReviewGallery\",\"url\":\"http://photos.example.com/share/a\",\"location\":\"Home\",\"date\":\"2026-10-22\"}";
        given().contentType(ContentType.JSON).body(body).post("/gallery").then().statusCode(400);
        int id = given().contentType(ContentType.JSON).body(body.replace("http:", "https:"))
                .post("/gallery").then().statusCode(201).extract().jsonPath().getInt("id");
        given().contentType(ContentType.JSON).body(body).put("/gallery/" + id).then().statusCode(400);
        given().get("/gallery").then().body("find { it.id == " + id + " }.url", is("https://photos.example.com/share/a"));
    }

    @Transactional
    void referenceRestrictedImage() {
        if (em.createQuery("SELECT COUNT(p) FROM ForumPost p WHERE p.id = 9300", Long.class).getSingleResult() == 0) {
            em.createNativeQuery("INSERT INTO FORUM_CATEGORY (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (9300,'RestrictedImageCategory',0,1,20,'Vorstand',9100)").executeUpdate();
            em.createNativeQuery("INSERT INTO FORUM_TOPIC (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (9300,'RestrictedImageTopic',0,1,0,9100,9300)").executeUpdate();
            em.createNativeQuery("INSERT INTO FORUM_POSTS (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (9300,'Private image','<p>Private</p>',0,0,0,0,9100,9300)").executeUpdate();
        }
        em.createQuery("UPDATE ForumPost p SET p.content = :html WHERE p.id = 9100")
                .setParameter("html", "<img src=\"/forum/img/9300/thumb/private.jpg?exp=9999999999&amp;sig=supplied\">")
                .executeUpdate();
    }

    @Test @TestSecurity(user = "reviewVisitor", roles = "Besucher")
    void publicPostCannotMintOrRedistributeRestrictedImageSignatures() {
        referenceRestrictedImage();
        given().queryParam("post", 9100).get("/forum/post").then().statusCode(200)
                .body("[0].content", not(containsString("sig=")))
                .body("[0].content", containsString("/forum/img/9300/full/private.jpg"));
    }

    @Test void authorisedReaderGetsBothImageSignatures() {
        referenceRestrictedImage();
        String content = given().queryParam("post", 9100).get("/forum/post").then().statusCode(200)
                .extract().jsonPath().getString("[0].content");
        assertTrue(content.contains("/9300/full/private.jpg?exp="));
        assertTrue(content.contains("/9300/thumb/private.jpg?exp="));
        assertTrue(content.contains("sig="));
        assertFalse(content.contains("sig=supplied"));
    }
}
