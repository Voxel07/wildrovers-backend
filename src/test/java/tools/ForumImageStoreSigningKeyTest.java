package tools;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForumImageStoreSigningKeyTest {

    private static ForumImageStore store(boolean required, String key) {
        ForumImageStore store = new ForumImageStore();
        store.requireSigningKey = required;
        store.configuredSigningKey = Optional.ofNullable(key);
        store.publicApiUrl = "http://localhost:8080";
        return store;
    }

    @Test
    void prodRefusesMissingOrShortKey() {
        assertThrows(IllegalStateException.class, () -> store(true, null).init());
        assertThrows(IllegalStateException.class, () -> store(true, "too-short").init());
        assertDoesNotThrow(() -> store(true, "x".repeat(ForumImageStore.MIN_SIGNING_KEY_LENGTH)).init());
    }

    @Test
    void otherProfilesFallBackToARandomKey() {
        assertDoesNotThrow(() -> store(false, null).init());
    }
}
