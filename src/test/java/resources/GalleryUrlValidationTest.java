package resources;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GalleryUrlValidationTest {
    @Test void requiresHttpsWithoutCredentialsOrUnexpectedPort() {
        GalleryResource resource = new GalleryResource();
        resource.allowedHost = Optional.empty();
        assertTrue(resource.validAlbumUrl("https://photos.example.com/share/a?x=1&y=2"));
        for (String url : new String[] {"http://photos.example.com", "javascript:alert(1)", "//photos.example.com",
                "https://user:password@photos.example.com", "https://photos.example.com:8080", "https://", "https://a b"}) {
            assertFalse(resource.validAlbumUrl(url), url);
        }
    }

    @Test void optionalHostIsAnExactMatch() {
        GalleryResource resource = new GalleryResource();
        resource.allowedHost = Optional.of("photos.example.com");
        assertTrue(resource.validAlbumUrl("https://PHOTOS.example.com/share/a"));
        assertFalse(resource.validAlbumUrl("https://photos.example.com.attacker.test/share/a"));
        assertFalse(resource.validAlbumUrl("https://other.example.com/share/a"));
    }
}
