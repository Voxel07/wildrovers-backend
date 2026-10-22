package tools;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import model.Forum.ForumPost;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ForumImageCleanupTest {

    @Inject
    ForumImageCleanup cleanup;

    @Inject
    ForumImageStore imageStore;

    @Inject
    EntityManager em;

    @Test
    void collectsReferencesFromAnyUrlForm() {
        Map<String, Set<String>> refs = new HashMap<>();
        ForumImageStore.collectReferences(
                "<img src=\"/forum/img/12/thumb/a.jpg\">"
                        + "<a href='https://api.example.org/forum/img/12/full/a.jpg?exp=1&amp;sig=x'>x</a>"
                        + "<img src=\"http://localhost:8080/forum/img/tmp_3_4/thumb/img_0.jpg\">",
                refs);
        assertEquals(Set.of("a.jpg"), refs.get("12"));
        assertEquals(Set.of("img_0.jpg"), refs.get("tmp_3_4"));
    }

    @Test
    void deletesOnlyOldUnreferencedFiles() throws Exception {
        Long postId = createPostReferencing("kept.jpg");
        Instant now = Instant.now();
        Instant old = now.minus(Duration.ofDays(3));

        Path postDir = imageStore.createDir(String.valueOf(postId), "full");
        Path kept = touch(postDir.resolve("kept.jpg"), old);
        Path orphan = touch(postDir.resolve("orphan.jpg"), old);
        Path fresh = touch(postDir.resolve("fresh.jpg"), now);

        Path deletedPostDir = imageStore.createDir("987654321", "thumb");
        Path deletedPostFile = touch(deletedPostDir.resolve("gone.jpg"), old);

        Path tmpDir = imageStore.createDir("tmp_1_" + now.toEpochMilli(), "full");
        Path recentTmp = touch(tmpDir.resolve("draft.jpg"), now.minus(Duration.ofHours(2)));
        Path oldTmpDir = imageStore.createDir("tmp_1_1", "full");
        Path oldTmp = touch(oldTmpDir.resolve("abandoned.jpg"), old);

        cleanup.cleanup(now);

        assertTrue(Files.exists(kept), "referenced file must stay");
        assertTrue(Files.exists(fresh), "files younger than the minimum age must stay");
        assertTrue(Files.exists(recentTmp), "unattached uploads stay for 24 h");
        assertFalse(Files.exists(orphan), "unreferenced file must be deleted");
        assertFalse(Files.exists(deletedPostFile), "files of deleted posts must be deleted");
        assertFalse(Files.exists(oldTmp), "abandoned uploads must be deleted");
        assertFalse(Files.exists(imageStore.postsRoot().resolve("987654321")), "empty directories are removed");
    }

    Long createPostReferencing(String file) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ForumPost post = new ForumPost("cleanup-test-" + System.nanoTime(), "", 0L, null, 0L, 0L);
            em.persist(post);
            post.setContent("<p><img src=\"/forum/img/" + post.getId() + "/full/" + file + "\"></p>");
            return post.getId();
        });
    }

    private static Path touch(Path file, Instant mtime) throws Exception {
        Files.writeString(file, "x");
        Files.setLastModifiedTime(file, FileTime.from(mtime));
        return file;
    }
}
