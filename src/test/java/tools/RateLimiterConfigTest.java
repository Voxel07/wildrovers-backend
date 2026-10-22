package tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterConfigTest {

    @Test
    void signupMatchesOnlyPutUserItself() {
        assertTrue(RateLimiter.Config.SIGNUP.matchesPath("/user"));
        assertTrue(RateLimiter.Config.SIGNUP.matchesPath("/user/"));
        assertFalse(RateLimiter.Config.SIGNUP.matchesPath("/user/me"));
        assertFalse(RateLimiter.Config.SIGNUP.matchesPath("/user/me/profile"));
    }

    @Test
    void prefixesMatchWholePathSegments() {
        assertTrue(RateLimiter.Config.USER_PROFILE_WRITE.matchesPath("/user/me/photo"));
        assertTrue(RateLimiter.Config.FORUM_READ.matchesPath("/forum"));
        assertTrue(RateLimiter.Config.FORUM_READ.matchesPath("/forum/topic"));
        assertFalse(RateLimiter.Config.FORUM_READ.matchesPath("/forumx"));
        assertTrue(RateLimiter.Config.FORUM_IMAGES.matchesPath("/forum/img/1/full/a.jpg"));
    }
}
