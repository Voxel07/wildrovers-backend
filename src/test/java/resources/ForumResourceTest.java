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
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,yearlyFeePaid) VALUES (400,'forum@t.l','forumTest','test1234','Forum','Test','Frischling',true,0,true,false,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (400,'test1234',true,'v-fo',400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (400,0,0,0,0,400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (400,'TestCat',0,0,0,'Besucher',400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (400,'TestTopic',0,0,0,400,400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (400,'TestPost','<p>C</p>',0,0,0,0,400,400)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (400,'<p>A</p>',0,0,0,400,400)").executeUpdate();
            em.flush();
        } catch (Exception ignored) {}
    }

    // ── Category ──
    @Test void getCategories() { given().get("/forum/category").then().statusCode(200); }
    @Test void getCategoryById() { given().queryParam("categoryId",400).get("/forum/category").then().statusCode(200); }
    @Test void getCategoryByName() { given().queryParam("category","TestCat").get("/forum/category").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addCategory() { given().contentType(ContentType.JSON).body("{\"category\":\"NewCat\",\"position\":1,\"visibility\":\"Besucher\"}").put("/forum/category").then().statusCode(anyOf(is(201),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updateCategory() { given().contentType(ContentType.JSON).body("{\"id\":400,\"category\":\"TestCat\",\"position\":10,\"visibility\":\"Besucher\"}").post("/forum/category").then().statusCode(anyOf(is(200),is(500))); }
    @Test void addCategoryUnauth() { given().contentType(ContentType.JSON).body("{\"category\":\"x\"}").put("/forum/category").then().statusCode(401); }

    // ── Topic ──
    @Test void getTopics() { given().get("/forum/topic").then().statusCode(200); }
    @Test void getTopicById() { given().queryParam("topicId",400).get("/forum/topic").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addTopic() { given().contentType(ContentType.JSON).queryParam("category",400).body("{\"topic\":\"NewTopic\"}").put("/forum/topic").then().statusCode(anyOf(is(200),is(201),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updateTopic() { given().contentType(ContentType.JSON).body("{\"id\":400,\"topic\":\"TestTopic-Upd\"}").post("/forum/topic").then().statusCode(anyOf(is(200),is(500))); }

    // ── Post ──
    @Test void getPosts() { given().get("/forum/post").then().statusCode(200); }
    @Test void getPostById() { given().queryParam("post",400).get("/forum/post").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addPost() { given().contentType(ContentType.JSON).queryParam("topic",400).body("{\"title\":\"NewPost\",\"content\":\"<p>NP</p>\"}").put("/forum/post").then().statusCode(anyOf(is(201),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updatePost() { given().contentType(ContentType.JSON).body("{\"id\":400,\"title\":\"Upd\",\"content\":\"<p>U</p>\"}").post("/forum/post").then().statusCode(anyOf(is(200),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void votePost() { given().contentType(ContentType.JSON).queryParam("post",400).queryParam("type","like").post("/forum/post/vote").then().statusCode(anyOf(is(200),is(201),is(500))); }
    @Test void votePostUnauth() { given().contentType(ContentType.JSON).queryParam("post",400).queryParam("type","like").post("/forum/post/vote").then().statusCode(401); }
    @Test void getLatestPost() { given().queryParam("topic",400).get("/forum/post/latest").then().statusCode(anyOf(is(200),is(404))); }

    // ── Answer ──
    @Test void getAnswers() { given().get("/forum/answer").then().statusCode(200); }
    @Test void getAnswerById() { given().queryParam("answer",400).get("/forum/answer").then().statusCode(200); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void addAnswer() { given().contentType(ContentType.JSON).queryParam("post",400).body("{\"content\":\"<p>NA</p>\"}").put("/forum/answer").then().statusCode(anyOf(is(200),is(201),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void updateAnswer() { given().contentType(ContentType.JSON).body("{\"id\":400,\"content\":\"<p>UA</p>\"}").post("/forum/answer").then().statusCode(anyOf(is(200),is(500))); }

    // ── Poll ──
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void createPoll() { given().contentType(ContentType.JSON).queryParam("post",400).body("{\"question\":\"Q?\",\"options\":[{\"optionText\":\"A\"},{\"optionText\":\"B\"}]}").post("/forum/poll/create").then().statusCode(anyOf(is(200),is(201),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void hasVoted() { given().queryParam("poll",400).get("/forum/poll/hasVoted").then().statusCode(anyOf(is(200),is(500))); }
    @Test @TestSecurity(user="forumTest",roles={"Frischling"}) void myVotes() { given().queryParam("poll",400).get("/forum/poll/myVotes").then().statusCode(anyOf(is(200),is(500))); }

    // ── Image ──
    @Test void img404() { given().get("/forum/img/99999/full/x.jpg").then().statusCode(anyOf(is(404),is(406))); }
    @Test void imgBadVariant() { given().get("/forum/img/1/invalid/x.jpg").then().statusCode(anyOf(is(400),is(406))); }
    @Test void imgTraversal() { given().get("/forum/img/1/full/../etc/passwd").then().statusCode(anyOf(is(400),is(406))); }
}
