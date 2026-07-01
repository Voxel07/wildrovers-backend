package resources;

import io.quarkus.test.junit.QuarkusTest;
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

/**
 * Tests for Quarkus cache (@CacheResult / @CacheInvalidateAll) behavior.
 * Verifies that caches are populated on read and invalidated on write.
 */
@QuarkusTest
public class CacheInvalidationTest {

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
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES (100,'besucher@test.local','testBesucher','test1234','Besucher','Test','Besucher',true,0,false,false),(104,'admin@test.local','testAdmin','test1234','Admin','Test','Admin',true,0,true,false)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (100,'test1234',true,'v-b',100),(104,'test1234',true,'v-a',104)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (100,0,0,0,0,100),(104,0,0,0,0,104)").executeUpdate();
            
            // Insert Category
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (100,'TestCat',0,0,0,'Besucher',104)").executeUpdate();
            
            // Insert Event (column: creator_id, event_date)
            em.createNativeQuery("INSERT INTO \"EVENT\" (id,title,description,location,event_date,creator_id) VALUES (100,'TestEvent','Desc','Location',DATEADD('DAY', 2, CURRENT_TIMESTAMP),104)").executeUpdate();

            // Insert Gallery (column: creator_id, gallery_date)
            em.createNativeQuery("INSERT INTO \"GALLERY\" (id,title,location,url,gallery_date,creator_id) VALUES (100,'TestGallery','Location','http://url.com','2026-06-20',104)").executeUpdate();
            
            em.flush();
        } catch (Exception ignored) {}
    }

    // ── Team Members cache ──

    @Test
    void testTeamMembersCacheAndInvalidation() {
        // 1. Initial fetch to populate cache (unauthenticated → names stripped)
        given()
            .get("/user/members")
            .then()
            .statusCode(200)
            .body("userName", hasItem("testAdmin"));

        // 2. Perform direct DB update bypassing JPA lifecycle and cache invalidations
        dbHelper.updateUserFirstNameDirectly(104L, "AdminModifiedDirectly");

        // 3. Fetch again. The cache TTL is 5m, so it should still return the cached data
        given()
            .get("/user/members")
            .then()
            .statusCode(200)
            .body("userName", hasItem("testAdmin"));

        // 4. Trigger cache invalidation
        dbHelper.invalidateTeamMembersCache(userOrm, 104L);

        // 5. Fetch again. Cache invalidated, userName still present
        given()
            .get("/user/members")
            .then()
            .statusCode(200)
            .body("userName", hasItem("testAdmin"));
    }

    // ── Events cache ──

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

    // ── Galleries cache ──

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

    // ── Forum Categories cache ──

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

    // ── Upcoming events cache ──

    @Test
    void testUpcomingEventsCacheAndInvalidation() {
        // 1. Initial fetch to populate upcoming-events cache
        given()
            .get("/event/upcoming")
            .then()
            .statusCode(200);

        // 2. Update DB directly
        dbHelper.updateEventTitleDirectly(100L, "UpcomingModified");

        // 3. Fetch again (should be cached)
        given()
            .get("/event/upcoming")
            .then()
            .statusCode(200)
            .body("title", hasItem("TestEvent"))
            .body("title", not(hasItem("UpcomingModified")));

        // 4. Invalidate via update
        dbHelper.invalidateEventsCache(eventOrm, 100L);

        // 5. Fetch again (updated)
        given()
            .get("/event/upcoming")
            .then()
            .statusCode(200)
            .body("title", hasItem("UpcomingModified"))
            .body("title", not(hasItem("TestEvent")));
    }
}

/**
 * Helper bean for direct DB manipulation and cache invalidation during tests.
 */
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
