package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import model.User;
import model.Users.Secret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserDeletionTest {
    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        try {
            // Admin user + full forum data
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,yearlyFeePaid) VALUES (500,'del@t.l','delTest','test1234','Del','Test','Admin',true,0,true,false,true)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (500,'test1234',true,'v-del',500)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (500,0,0,0,0,500)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (500,'DelCat',0,0,0,'Besucher',500)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (500,'DelTopic',0,0,0,500,500)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (500,'DelPost','<p>Keep me</p>',0,0,0,0,500,500)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (500,'<p>Keep answer</p>',0,0,0,500,500)").executeUpdate();
            em.flush();
        } catch (Exception ignored) {}
    }

    @Transactional
    Long createUser(String username, String role) {
        Long id = ((Number) em.createNativeQuery("SELECT NEXT VALUE FOR ZSEQ_USER_ID").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,yearlyFeePaid) VALUES (:id,:em,:un,'test1234','T','T',:role,true,0,:cc,false,false)")
            .setParameter("id",id).setParameter("em",username+"@t.l").setParameter("un",username).setParameter("role",role).setParameter("cc",!"Besucher".equals(role)).executeUpdate();
        Long sid = ((Number) em.createNativeQuery("SELECT NEXT VALUE FOR ZSEQ_KEYS_ID").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (:id,'test1234',true,:vid,:uid)").setParameter("id",sid).setParameter("vid","v-"+username).setParameter("uid",id).executeUpdate();
        // Create sequence if missing (entity has @SequenceGenerator/@GeneratedValue mismatch)
        try { em.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS ZSEQ_AF_ID START WITH 1000").executeUpdate(); } catch (Exception ignored) {}
        Long aid = ((Number) em.createNativeQuery("SELECT NEXT VALUE FOR ZSEQ_AF_ID").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (:id,0,0,0,0,:uid)").setParameter("id",aid).setParameter("uid",id).executeUpdate();
        em.flush();
        return id;
    }

    // Role-based access
    @Test @TestSecurity(user="delTest",roles={"Admin"}) void adminCanDelete() {
        Long uid = createUser("toDel","Mitglied");
        given().delete("/user/"+uid).then().statusCode(200).body(containsString("gelöscht"));
    }
    @Test void unauthCantDelete() { given().delete("/user/500").then().statusCode(401); }

    // Forum posts preserved after user deletion
    @Test @TestSecurity(user="delTest",roles={"Admin"}) void postsSurviveDeletion() {
        Long uid = createUser("author","Frischling");

        // Create a post via committed helper, then verify survival
        Long postId = createPostForUser(uid, "Surviving Post", "<p>Must survive</p>");

        // Delete user via REST
        given().delete("/user/"+uid).then().statusCode(200);

        // Post must survive
        em.clear();
        ForumPost surviving = em.find(ForumPost.class, postId);
        assertNotNull(surviving, "Post must survive user deletion");
        assertEquals("Surviving Post", surviving.getTitle());
        assertNull(surviving.getCreatorObj(), "Creator FK must be nullified");
    }

    @Transactional
    Long createPostForUser(Long userId, String title, String content) {
        ForumPost post = new ForumPost();
        post.setTitle(title);
        post.setContent(content);
        post.setCreationDate(System.currentTimeMillis());
        post.setLikes(0L); post.setDislikes(0L); post.setAnswerCount(0L);
        post.setCreator(em.find(User.class, userId));
        post.setTopic(em.find(ForumTopic.class, 500L));
        em.persist(post);
        em.flush();
        return post.getId();
    }

    // Secret deleted with user
    @Test @TestSecurity(user="delTest",roles={"Admin"}) void secretDeletedWithUser() {
        Long uid = createUser("secDel","Besucher");
        em.clear();
        Secret s = (Secret) em.createQuery("SELECT s FROM Secret s WHERE s.user.id=:uid").setParameter("uid",uid).getSingleResult();
        assertNotNull(s);
        given().delete("/user/"+uid).then().statusCode(200);
        em.clear();
        Long cnt = em.createQuery("SELECT COUNT(s) FROM Secret s WHERE s.user.id=:uid",Long.class).setParameter("uid",uid).getSingleResult();
        assertEquals(0L, cnt, "Secret must be deleted");
    }

    // Pre-existing forum taxonomy survives
    @Test @TestSecurity(user="delTest",roles={"Admin"}) void preExistingContentSurvives() {
        Long uid = createUser("tempDel","Mitglied");
        given().delete("/user/"+uid).then().statusCode(200);
        em.clear();
        assertNotNull(em.find(model.Forum.ForumCategory.class, 500L));
        assertNotNull(em.find(model.Forum.ForumTopic.class, 500L));
        assertNotNull(em.find(ForumPost.class, 500L));
        assertNotNull(em.find(model.Forum.ForumAnswer.class, 500L));
    }
}
