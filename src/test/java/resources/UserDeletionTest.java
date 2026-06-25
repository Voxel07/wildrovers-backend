package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import model.Forum.ForumCategory;
import model.Forum.ForumAnswer;
import model.User;
import model.Event;
import model.Gallery;
import model.EventAttendance;
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
            em.createNativeQuery("DELETE FROM \"FORUM_ANSWERS\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"FORUM_POSTS\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"FORUM_TOPIC\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"FORUM_CATEGORY\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"EVENT_ATTENDANCES\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"EVENT\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"GALLERY\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"SECRET\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"ACTVITY_FORUM\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"USER\"").executeUpdate();
            em.flush();

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

    @Transactional
    void persistEntities(Object... entities) {
        for (Object entity : entities) {
            em.persist(entity);
        }
        em.flush();
    }

    @Transactional
    void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @TestSecurity(user="delTest", roles={"Admin"})
    void deleteUserWithAllActivities() {
        // 1. Add user with rank/role of member
        Long memberId = createUser("activityMember", "Mitglied");
        User member = em.find(User.class, memberId);
        assertNotNull(member, "Member user should be created");

        // 2. Create category, topic, posts, and answers in the forum, create an event and add gallery
        ForumCategory category = new ForumCategory();
        category.setCategory("TestMemberCategory");
        category.setCreationDate(System.currentTimeMillis());
        category.setTopicCount(0L);
        category.setPosition(0L);
        category.setVisibility("Besucher");
        category.setCreator(member);

        ForumTopic topic = new ForumTopic();
        topic.setTopic("TestMemberTopic");
        topic.setCreationDate(System.currentTimeMillis());
        topic.setPostCount(0L);
        topic.setViews(0L);
        topic.setCreator(member);
        topic.setCategory(category);

        ForumPost post = new ForumPost();
        post.setTitle("TestMemberPost");
        post.setContent("<p>Post content</p>");
        post.setCreationDate(System.currentTimeMillis());
        post.setLikes(0L);
        post.setDislikes(0L);
        post.setAnswerCount(0L);
        post.setCreator(member);
        post.setTopic(topic);

        ForumAnswer answer = new ForumAnswer();
        answer.setContent("<p>Answer content</p>");
        answer.setCreationDate(System.currentTimeMillis());
        answer.setLikes(0L);
        answer.setDislikes(0L);
        answer.setCreator(member);
        answer.setPost(post);

        Event event = new Event();
        event.setTitle("TestMemberEvent");
        event.setDescription("Description");
        event.setLocation("Location");
        event.setEventDate(java.time.LocalDateTime.now().plusDays(2));
        event.setCreator(member);

        Gallery gallery = new Gallery();
        gallery.setTitle("TestMemberGallery");
        gallery.setLocation("Gallery Location");
        gallery.setUrl("http://example.com/gallery/test");
        gallery.setDate(java.time.LocalDate.now());
        gallery.setCreator(member);

        persistEntities(category, topic, post, answer, event, gallery);

        EventAttendance attendance = new EventAttendance();
        attendance.setUser(member);
        attendance.setEvent(event);
        attendance.setStatus("YES");

        persistEntities(attendance);

        flushAndClear();

        // 3. Check if they are actually created
        assertNotNull(em.find(User.class, memberId), "User must exist");
        assertNotNull(em.find(ForumCategory.class, category.getId()), "Category must exist");
        assertNotNull(em.find(ForumTopic.class, topic.getId()), "Topic must exist");
        assertNotNull(em.find(ForumPost.class, post.getId()), "Post must exist");
        assertNotNull(em.find(ForumAnswer.class, answer.getId()), "Answer must exist");
        assertNotNull(em.find(Event.class, event.getId()), "Event must exist");
        assertNotNull(em.find(Gallery.class, gallery.getId()), "Gallery must exist");
        assertNotNull(em.find(EventAttendance.class, attendance.getId()), "Attendance must exist");

        // 4. Delete the user and all his activities (with deletePosts=true for smart cascade)
        given()
               .queryParam("deleteAccount", true)
               .queryParam("deleteEvents", true)
               .queryParam("deletePosts", true)
               .queryParam("deleteGallery", true)
               .delete("/user/" + memberId)
               .then()
               .statusCode(200);

        em.clear();

        // User is now blocked, not deleted (prevents OIDC re-provisioning)
        User blockedUser = em.find(User.class, memberId);
        assertNotNull(blockedUser, "User must still exist (blocked, not deleted)");
        assertTrue(blockedUser.getIsBlocked(), "User must be blocked");
        assertFalse(blockedUser.getIsActive(), "User must be inactive");

        // Events, gallery, and attendance must be deleted
        assertNull(em.find(Event.class, event.getId()), "User's event must be deleted");
        assertNull(em.find(Gallery.class, gallery.getId()), "User's gallery entry must be deleted");
        assertNull(em.find(EventAttendance.class, attendance.getId()), "Event attendance must be deleted");

        // Smart cascade: answers by user are deleted
        assertNull(em.find(ForumAnswer.class, answer.getId()), "User's answer must be deleted");

        // Smart cascade: post with no remaining answers is deleted
        assertNull(em.find(ForumPost.class, post.getId()), "Empty post must be deleted");

        // Smart cascade: topic with no remaining posts is deleted
        assertNull(em.find(ForumTopic.class, topic.getId()), "Empty topic must be deleted");

        // Category is never deleted, only creator nullified
        ForumCategory deletedUserCategory = em.find(ForumCategory.class, category.getId());
        assertNotNull(deletedUserCategory, "Category must survive");
        assertEquals("deleted", deletedUserCategory.getCreator(), "Category creator must be nullified");
    }

    /**
     * Smart cascade: if user's post has answers from other users, the post survives
     * with creator nullified. The topic also survives because it still has posts.
     */
    @Test
    @TestSecurity(user="delTest", roles={"Admin"})
    void smartCascadePreservesContentWithOtherUsers() {
        Long authorId = createUser("smartAuthor", "Mitglied");
        Long otherId = createUser("otherUser", "Mitglied");

        // Author creates a topic and a post
        ForumTopic topic = new ForumTopic();
        topic.setTopic("SmartTopic");
        topic.setCreationDate(System.currentTimeMillis());
        topic.setPostCount(0L);
        topic.setViews(0L);
        topic.setCreator(em.find(User.class, authorId));
        topic.setCategory(em.find(ForumCategory.class, 500L));

        ForumPost post = new ForumPost();
        post.setTitle("SmartPost");
        post.setContent("<p>Content</p>");
        post.setCreationDate(System.currentTimeMillis());
        post.setLikes(0L); post.setDislikes(0L); post.setAnswerCount(0L);
        post.setCreator(em.find(User.class, authorId));
        post.setTopic(topic);

        persistEntities(topic, post);

        // Other user adds an answer to the author's post
        ForumAnswer otherAnswer = new ForumAnswer();
        otherAnswer.setContent("<p>Other user's answer</p>");
        otherAnswer.setCreationDate(System.currentTimeMillis());
        otherAnswer.setLikes(0L); otherAnswer.setDislikes(0L);
        otherAnswer.setCreator(em.find(User.class, otherId));
        otherAnswer.setPost(post);
        persistEntities(otherAnswer);

        flushAndClear();

        // Delete the author with deletePosts=true
        given()
               .queryParam("deleteAccount", true)
               .queryParam("deleteEvents", true)
               .queryParam("deletePosts", true)
               .queryParam("deleteGallery", true)
               .delete("/user/" + authorId)
               .then().statusCode(200);

        em.clear();

        // Post must survive because it has an answer from another user
        ForumPost survivingPost = em.find(ForumPost.class, post.getId());
        assertNotNull(survivingPost, "Post with other user's answer must survive");
        assertNull(survivingPost.getCreatorObj(), "Post creator must be nullified");

        // Answer from other user must still exist
        ForumAnswer survivingAnswer = em.find(ForumAnswer.class, otherAnswer.getId());
        assertNotNull(survivingAnswer, "Other user's answer must survive");

        // Topic must survive because it still has posts
        ForumTopic survivingTopic = em.find(ForumTopic.class, topic.getId());
        assertNotNull(survivingTopic, "Topic with remaining posts must survive");
    }
}
