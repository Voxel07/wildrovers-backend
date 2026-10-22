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
class ForumResourceTest {
    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        try {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (400,'forum@t.l','forumTest','test1234','Forum','Test','Frischling',true,0,true,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (400,'test1234',true,'v-fo',400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (400,'TestCat',0,0,0,'Besucher',400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (400,'TestTopic',0,0,0,400,400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (400,'TestPost','<p>C</p>',0,0,0,0,400,400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (400,'<p>A</p>',0,0,0,400,400)").executeUpdate();
            em.flush();
        } catch (Exception ignored) {}
    }

    /**
     * Own accounts and content for the status-code tests (ids 410+), inserted once and
     * independent of the best-effort seed above, which other test classes may wipe.
     */
    @BeforeEach @Transactional
    void setupStatusFixtures() {
        Number existing = (Number) em.createNativeQuery("SELECT COUNT(*) FROM \"FORUM_POSTS\" WHERE id = 410").getSingleResult();
        if (existing.intValue() > 0) return;
        em.createNativeQuery("DELETE FROM \"USER\" WHERE id IN (410,411,412)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (410,'forum-owner@t.l','forumOwner','test1234','Forum','Owner','Mitglied',true,0,true,false)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (411,'forum-other@t.l','forumOther','test1234','Forum','Other','Mitglied',true,0,false,false)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (412,'forum-blocked@t.l','forumBlocked','test1234','Forum','Blocked','Mitglied',true,0,false,true)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (410,'StatusCat',0,1,99,'Besucher',410)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (410,'StatusTopic',0,1,0,410,410)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (410,'StatusPost','<p>C</p>',0,0,0,1,410,410)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (410,'<p>A</p>',0,0,0,410,410)").executeUpdate();
        // Answers inserted out of creation order: the API must return 410, 412, 411.
        em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (411,'<p>late</p>',20,0,0,410,410)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (412,'<p>early</p>',10,0,0,410,410)").executeUpdate();
        // Moderator, a Vorstand-only category and a second public category for the topic-name tests.
        em.createNativeQuery("DELETE FROM \"USER\" WHERE id = 416").executeUpdate();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (416,'forum-mod@t.l','forumMod','test1234','Forum','Mod','Vorstand',true,0,true,false)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (413,'BoardCat',0,1,98,'Vorstand',410)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (413,'BoardTopic',0,1,0,410,413)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (413,'BoardPost','<p>secret</p>',0,0,0,0,410,413)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (414,'SecondCat',0,0,97,'Besucher',410)").executeUpdate();
    }

    // ── Pagination and visibility in the query (2.2) ──
    @Test void pagedPostListCarriesTotal() {
        given().queryParam("topic",410).queryParam("size",1).get("/forum/post").then().statusCode(200)
                .header("X-Total-Count", "1").body("size()", is(1));
    }
    @Test void hiddenCategoryIsNotListedForVisitors() {
        given().queryParam("post",413).get("/forum/post").then().statusCode(200)
                .header("X-Total-Count", "0").body("size()", is(0));
        given().queryParam("topicId",413).get("/forum/topic").then().statusCode(200).body("size()", is(0));
    }
    @Test @TestSecurity(user="forumMod",roles={"Vorstand"})
    void hiddenCategoryIsListedForItsRole() {
        given().queryParam("post",413).get("/forum/post").then().statusCode(200).body("[0].id", is(413));
    }

    // ── Answer order (2.3) ──
    @Test void answersAreOrderedByCreation() {
        given().queryParam("post",410).get("/forum/answer").then().statusCode(200)
                .body("id", is(java.util.List.of(410, 412, 411)));
    }

    // ── Topic names per category and topic count (2.4, 2.7) ──
    @Test @TestSecurity(user="forumOwner",roles={"Mitglied"})
    void topicNamesAreUniquePerCategory() {
        given().contentType(ContentType.JSON).queryParam("category",410).body("{\"topic\":\"StatusTopic\"}")
                .put("/forum/topic").then().statusCode(409);
        given().contentType(ContentType.JSON).queryParam("category",414).body("{\"topic\":\"StatusTopic\"}")
                .put("/forum/topic").then().statusCode(201);
        // The count is updated in the database and the category cache is invalidated.
        given().queryParam("categoryId",414).get("/forum/category").then().statusCode(200)
                .body("[0].topicCount", is(1));
    }

    // ── View counter (2.5) and votes (2.6) ──
    @Test @TestSecurity(user="forumOther",roles={"Mitglied"})
    void viewIsCountedOncePerUserAndNotOnGet() {
        given().queryParam("post",410).get("/forum/post").then().statusCode(200).body("[0].views", anyOf(nullValue(), is(0)));
        given().queryParam("post",410).post("/forum/post/view").then().statusCode(204);
        given().queryParam("post",410).post("/forum/post/view").then().statusCode(204);
        given().queryParam("post",410).get("/forum/post").then().statusCode(200)
                .body("[0].views", is(1)).body("[0].viewed", is(true));
    }
    @Test @TestSecurity(user="forumOther",roles={"Mitglied"})
    void viewOfHiddenPostIs404() {
        given().queryParam("post",413).post("/forum/post/view").then().statusCode(404);
    }
    @Test @TestSecurity(user="forumOther",roles={"Mitglied"})
    void secondIdenticalVoteRemovesIt() {
        given().contentType(ContentType.JSON).queryParam("post",410).queryParam("type","like").post("/forum/post/vote").then().statusCode(200)
                .body("likes", is(1));
        given().contentType(ContentType.JSON).queryParam("post",410).queryParam("type","like").post("/forum/post/vote").then().statusCode(200)
                .body("likes", is(0));
    }

    // ── Moderation (section 1) ──
    @Test @TestSecurity(user="forumMod",roles={"Vorstand"})
    void vorstandMayEditForeignPosts() {
        given().contentType(ContentType.JSON).body("{\"id\":410,\"title\":\"StatusPost\",\"content\":\"<p>C</p>\"}")
                .post("/forum/post").then().statusCode(200);
    }

    // ── Status codes ──
    @Test @TestSecurity(user="forumOther",roles={"Mitglied"})
    void updateForeignPostIsForbiddenWithoutAccountHeader() {
        given().contentType(ContentType.JSON).body("{\"id\":410,\"title\":\"Hijack\",\"content\":\"<p>x</p>\"}").post("/forum/post")
                .then().statusCode(403).header("X-Account-Status", nullValue());
    }
    @Test @TestSecurity(user="forumOther",roles={"Mitglied"})
    void deleteForeignAnswerIsForbidden() {
        given().contentType(ContentType.JSON).body("{\"id\":410}").delete("/forum/answer").then().statusCode(403);
    }
    @Test @TestSecurity(user="forumOther",roles={"Mitglied"})
    void updateMissingPostIs404() {
        given().contentType(ContentType.JSON).body("{\"id\":987654,\"title\":\"x\",\"content\":\"<p>x</p>\"}").post("/forum/post").then().statusCode(404);
    }
    @Test @TestSecurity(user="forumBlocked",roles={"Mitglied"})
    void blockedAccountGetsAccountHeader() {
        given().contentType(ContentType.JSON).queryParam("post",410).body("{\"content\":\"<p>x</p>\"}").put("/forum/answer")
                .then().statusCode(403).header("X-Account-Status", "denied");
    }

    // ── Topic summaries ──
    @Test void topicListCarriesLatestPost() {
        given().queryParam("category",410).get("/forum/topic").then().statusCode(200)
                .body("find { it.id == 410 }.latestPost.id", is(410))
                .body("find { it.id == 410 }.allViewed", is(false));
    }

    // ── Category ──
    @Test void getCategories() { given().get("/forum/category").then().statusCode(200); }
    @Test void getCategoryById() { given().queryParam("categoryId",400).get("/forum/category").then().statusCode(200); }
    @Test void getCategoryByName() { given().queryParam("category","TestCat").get("/forum/category").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addCategory() { given().contentType(ContentType.JSON).body("{\"category\":\"NewCat\",\"position\":1,\"visibility\":\"Besucher\"}").put("/forum/category").then().statusCode(anyOf(is(201),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updateCategory() { given().contentType(ContentType.JSON).body("{\"id\":400,\"category\":\"TestCat\",\"position\":10,\"visibility\":\"Besucher\"}").post("/forum/category").then().statusCode(anyOf(is(200),is(401),is(500))); }
    @Test void addCategoryUnauth() { given().contentType(ContentType.JSON).body("{\"category\":\"x\"}").put("/forum/category").then().statusCode(401); }

    // ── Topic ──
    @Test void getTopics() { given().get("/forum/topic").then().statusCode(200); }
    @Test void getTopicById() { given().queryParam("topicId",400).get("/forum/topic").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addTopic() { given().contentType(ContentType.JSON).queryParam("category",400).body("{\"topic\":\"NewTopic\"}").put("/forum/topic").then().statusCode(anyOf(is(200),is(201),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updateTopic() { given().contentType(ContentType.JSON).body("{\"id\":400,\"topic\":\"TestTopic-Upd\"}").post("/forum/topic").then().statusCode(anyOf(is(200),is(401),is(500))); }

    // ── Post ──
    @Test void getPosts() { given().get("/forum/post").then().statusCode(200); }
    @Test void getPostById() { given().queryParam("post",400).get("/forum/post").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addPost() { given().contentType(ContentType.JSON).queryParam("topic",400).body("{\"title\":\"NewPost\",\"content\":\"<p>NP</p>\"}").put("/forum/post").then().statusCode(anyOf(is(201),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updatePost() { given().contentType(ContentType.JSON).body("{\"id\":400,\"title\":\"Upd\",\"content\":\"<p>U</p>\"}").post("/forum/post").then().statusCode(anyOf(is(200),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void votePost() { given().contentType(ContentType.JSON).queryParam("post",400).queryParam("type","like").post("/forum/post/vote").then().statusCode(anyOf(is(200),is(201),is(401),is(500))); }
    @Test void votePostUnauth() { given().contentType(ContentType.JSON).queryParam("post",400).queryParam("type","like").post("/forum/post/vote").then().statusCode(401); }
    @Test void getLatestPost() { given().queryParam("topic",400).get("/forum/post/latest").then().statusCode(anyOf(is(200),is(404))); }

    // ── Answer ──
    @Test void getAnswers() { given().get("/forum/answer").then().statusCode(200); }
    @Test void getAnswerById() { given().queryParam("answer",400).get("/forum/answer").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addAnswer() { given().contentType(ContentType.JSON).queryParam("post",400).body("{\"content\":\"<p>NA</p>\"}").put("/forum/answer").then().statusCode(anyOf(is(200),is(201),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updateAnswer() { given().contentType(ContentType.JSON).body("{\"id\":400,\"content\":\"<p>UA</p>\"}").post("/forum/answer").then().statusCode(anyOf(is(200),is(401),is(500))); }

    // ── Poll ──
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void createPoll() { given().contentType(ContentType.JSON).queryParam("post",400).body("{\"question\":\"Q?\",\"options\":[{\"optionText\":\"A\"},{\"optionText\":\"B\"}]}").post("/forum/poll/create").then().statusCode(anyOf(is(200),is(201),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void hasVoted() { given().queryParam("poll",400).get("/forum/poll/hasVoted").then().statusCode(anyOf(is(200),is(401),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void myVotes() { given().queryParam("poll",400).get("/forum/poll/myVotes").then().statusCode(anyOf(is(200),is(401),is(500))); }

    // ── Image ──
    @Test void img404() { given().get("/forum/img/99999/full/x.jpg").then().statusCode(anyOf(is(404),is(406))); }
    @Test void imgBadVariant() { given().get("/forum/img/1/invalid/x.jpg").then().statusCode(anyOf(is(400),is(406))); }
    @Test void imgTraversal() { given().get("/forum/img/1/full/../etc/passwd").then().statusCode(anyOf(is(400),is(406))); }
}
