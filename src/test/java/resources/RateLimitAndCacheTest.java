package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import orm.UserOrm;
import orm.EventOrm;
import orm.GalleryOrm;
import orm.Forum.ForumCategoryOrm;
import model.Event;
import model.Gallery;
import model.Forum.ForumCategory;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
public class RateLimitAndCacheTest {

    @Inject
    EntityManager em;

    @Inject
    UserOrm userOrm;

    @Inject
    EventOrm eventOrm;

    @Inject
    GalleryOrm galleryOrm;

    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @Inject
    TestDbHelper dbHelper;

    @BeforeEach
    @Transactional
    void setup() {
        try {
            // Clean up to ensure test isolation
            em.createNativeQuery("DELETE FROM \"SECRET\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"ACTVITY_FORUM\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"FORUM_CATEGORY\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"EVENT\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"GALLERY\"").executeUpdate();
            em.createNativeQuery("DELETE FROM \"USER\"").executeUpdate();
            em.flush();

            // Insert test users
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,yearlyFeePaid) VALUES (100,'besucher@test.local','testBesucher','test1234','Besucher','Test','Besucher',true,0,false,false,false),(104,'admin@test.local','testAdmin','test1234','Admin','Test','Admin',true,0,true,false,true)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (100,'test1234',true,'v-b',100),(104,'test1234',true,'v-a',104)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (100,0,0,0,0,100),(104,0,0,0,0,104)").executeUpdate();
            
            // Insert Category
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (100,'TestCat',0,0,0,'Besucher',104)").executeUpdate();
            
            // Insert Event
            em.createNativeQuery("INSERT INTO \"EVENT\" (id,title,description,location,eventDate,user_id) VALUES (100,'TestEvent','Desc','Location','2026-06-20 10:00:00',104)").executeUpdate();

            // Insert Gallery
            em.createNativeQuery("INSERT INTO \"GALLERY\" (id,title,location,url,date,user_id) VALUES (100,'TestGallery','Location','http://url.com','2026-06-20',104)").executeUpdate();
            
            em.flush();
        } catch (Exception ignored) {}
    }

    @Test
    void testSignupRateLimit() {
        // Limit is 3 per minute
        // First 3 calls should return 201 (Created) or 406 (duplicate), but not 429
        for (int i = 0; i < 3; i++) {
            given()
                .contentType(ContentType.JSON)
                .body("{\"userName\":\"testUserRate" + i + "\",\"email\":\"testrate" + i + "@test.local\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}")
                .put("/user")
                .then()
                .statusCode(anyOf(is(201), is(406)));
        }

        // 4th call must be rate-limited with 429
        given()
            .contentType(ContentType.JSON)
            .body("{\"userName\":\"testUserRate3\",\"email\":\"testrate3@test.local\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}")
            .put("/user")
            .then()
            .statusCode(429);
    }

    @Test
    void testTeamMembersCacheAndInvalidation() {
        // 1. Initial fetch to populate cache.
        given()
            .get("/user/members")
            .then()
            .statusCode(200)
            .body("firstName", hasItem("Admin"))
            .body("firstName", not(hasItem("AdminModifiedDirectly")));

        // 2. Perform direct DB update bypassing JPA lifecycle and cache invalidations
        dbHelper.updateUserFirstNameDirectly(104L, "AdminModifiedDirectly");

        // 3. Fetch again. The cache TTL is 5m, so it should still return the cached 'Admin'
        given()
            .get("/user/members")
            .then()
            .statusCode(200)
            .body("firstName", hasItem("Admin"))
            .body("firstName", not(hasItem("AdminModifiedDirectly")));

        // 4. Trigger cache invalidation by calling a modifying method
        dbHelper.invalidateTeamMembersCache(userOrm, 104L);

        // 5. Fetch again. The cache should be invalidated, returning 'AdminModifiedDirectly'
        given()
            .get("/user/members")
            .then()
            .statusCode(200)
            .body("firstName", hasItem("AdminModifiedDirectly"))
            .body("firstName", not(hasItem("Admin")));
    }

    @Test
    void testEventsCacheAndInvalidation() {
        // 1. Initial fetch to populate cache
        given()
            .get("/event")
            .then()
            .statusCode(200)
            .body("title", hasItem("TestEvent"))
            .body("title", not(hasItem("EventModifiedDirectly")));

        // 2. Update DB directly
        dbHelper.updateEventTitleDirectly(100L, "EventModifiedDirectly");

        // 3. Fetch again (should still be cached as 'TestEvent')
        given()
            .get("/event")
            .then()
            .statusCode(200)
            .body("title", hasItem("TestEvent"))
            .body("title", not(hasItem("EventModifiedDirectly")));

        // 4. Invalidate cache
        dbHelper.invalidateEventsCache(eventOrm, 100L);

        // 5. Fetch again (should be updated)
        given()
            .get("/event")
            .then()
            .statusCode(200)
            .body("title", hasItem("EventModifiedDirectly"))
            .body("title", not(hasItem("TestEvent")));
    }

    @Test
    void testGalleriesCacheAndInvalidation() {
        // 1. Initial fetch to populate cache
        given()
            .get("/gallery")
            .then()
            .statusCode(200)
            .body("title", hasItem("TestGallery"))
            .body("title", not(hasItem("GalleryModifiedDirectly")));

        // 2. Update DB directly
        dbHelper.updateGalleryTitleDirectly(100L, "GalleryModifiedDirectly");

        // 3. Fetch again (cached)
        given()
            .get("/gallery")
            .then()
            .statusCode(200)
            .body("title", hasItem("TestGallery"))
            .body("title", not(hasItem("GalleryModifiedDirectly")));

        // 4. Invalidate cache
        dbHelper.invalidateGalleriesCache(galleryOrm, 100L);

        // 5. Fetch again (updated)
        given()
            .get("/gallery")
            .then()
            .statusCode(200)
            .body("title", hasItem("GalleryModifiedDirectly"))
            .body("title", not(hasItem("TestGallery")));
    }

    @Test
    void testForumCategoriesCacheAndInvalidation() {
        // 1. Initial fetch to populate cache
        given()
            .get("/forum/category")
            .then()
            .statusCode(200)
            .body("category", hasItem("TestCat"))
            .body("category", not(hasItem("CategoryModifiedDirectly")));

        // 2. Update DB directly
        dbHelper.updateCategoryNameDirectly(100L, "CategoryModifiedDirectly");

        // 3. Fetch again (cached)
        given()
            .get("/forum/category")
            .then()
            .statusCode(200)
            .body("category", hasItem("TestCat"))
            .body("category", not(hasItem("CategoryModifiedDirectly")));

        // 4. Invalidate cache
        dbHelper.invalidateForumCategoriesCache(forumCategoryOrm, 100L);

        // 5. Fetch again (updated)
        given()
            .get("/forum/category")
            .then()
            .statusCode(200)
            .body("category", hasItem("CategoryModifiedDirectly"))
            .body("category", not(hasItem("TestCat")));
    }
}

@ApplicationScoped
class TestDbHelper {
    @Inject
    EntityManager em;

    @Transactional
    public void updateUserFirstNameDirectly(Long id, String newName) {
        em.createNativeQuery("UPDATE \"USER\" SET firstName = :name WHERE id = :id")
          .setParameter("name", newName)
          .setParameter("id", id)
          .executeUpdate();
    }

    @Transactional
    public void invalidateTeamMembersCache(UserOrm userOrm, Long userId) {
        userOrm.updateUserRole(userId, "Admin");
    }

    @Transactional
    public void updateEventTitleDirectly(Long id, String newTitle) {
        em.createNativeQuery("UPDATE \"EVENT\" SET title = :title WHERE id = :id")
          .setParameter("title", newTitle)
          .setParameter("id", id)
          .executeUpdate();
    }

    @Transactional
    public void invalidateEventsCache(EventOrm eventOrm, Long eventId) {
        Event event = eventOrm.getEventById(eventId);
        if (event != null) {
            eventOrm.updateEvent(event);
        }
    }

    @Transactional
    public void updateGalleryTitleDirectly(Long id, String newTitle) {
        em.createNativeQuery("UPDATE \"GALLERY\" SET title = :title WHERE id = :id")
          .setParameter("title", newTitle)
          .setParameter("id", id)
          .executeUpdate();
    }

    @Transactional
    public void invalidateGalleriesCache(GalleryOrm galleryOrm, Long galleryId) {
        Gallery gallery = galleryOrm.getGalleryById(galleryId);
        if (gallery != null) {
            galleryOrm.updateGallery(gallery);
        }
    }

    @Transactional
    public void updateCategoryNameDirectly(Long id, String newName) {
        em.createNativeQuery("UPDATE \"FORUM_CATEGORY\" SET category = :name WHERE id = :id")
          .setParameter("name", newName)
          .setParameter("id", id)
          .executeUpdate();
    }

    @Transactional
    public void invalidateForumCategoriesCache(ForumCategoryOrm categoryOrm, Long categoryId) {
        List<ForumCategory> cats = categoryOrm.getCategoriesById(categoryId);
        if (!cats.isEmpty()) {
            categoryOrm.updateCategory(cats.get(0), 104L);
        }
    }
}
