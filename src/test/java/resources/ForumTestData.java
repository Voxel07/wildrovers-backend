package resources;

import jakarta.persistence.EntityManager;

/** Delete dependent forum rows before resetting shared integration-test fixtures. */
final class ForumTestData {
    private ForumTestData() { }

    static void clear(EntityManager em) {
        for (String table : new String[] {"FORUM_POLL_OPTION_VOTES", "FORUM_POLL_VOTES", "FORUM_POLL_OPTIONS",
                "FORUM_POLLS", "FORUM_POST_VIEWS", "FORUM_POST_VOTES", "FORUM_ANSWERS", "FORUM_POSTS",
                "FORUM_TOPIC", "FORUM_CATEGORY"}) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
    }
}
