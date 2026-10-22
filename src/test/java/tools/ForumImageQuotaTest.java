package tools;

import jakarta.ws.rs.WebApplicationException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ForumImageQuotaTest {
    @TempDir Path directory;

    private ForumImageQuota quota(long total, long daily) {
        ForumImageQuota quota = new ForumImageQuota();
        quota.maxStorageBytes = total;
        quota.maxUserDailyBytes = daily;
        return quota;
    }

    @Test void dailyBudgetSurvivesRestartAndMovingTheImage() throws Exception {
        Path root = directory.resolve("posts");
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        quota(Long.MAX_VALUE, Long.MAX_VALUE).store(image, "jpg", root, "tmp_1_2", "a.jpg", 1L);
        long bytes = Files.size(root.resolve("tmp_1_2/full/a.jpg")) + Files.size(root.resolve("tmp_1_2/thumb/a.jpg"));
        Files.move(root.resolve("tmp_1_2"), root.resolve("42"));
        ForumImageQuota restarted = quota(Long.MAX_VALUE, bytes);
        WebApplicationException error = assertThrows(WebApplicationException.class,
                () -> restarted.store(image, "jpg", root, "tmp_1_3", "b.jpg", 1L));
        assertEquals(429, error.getResponse().getStatus());
        assertFalse(Files.exists(root.resolve("tmp_1_3")));
        // Another user has their own budget.
        restarted.store(image, "jpg", root, "tmp_2_3", "c.jpg", 2L);
    }

    @Test void storageBudgetCountsExistingFilesAndThumbnails() throws Exception {
        Path root = directory.resolve("posts");
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        quota(Long.MAX_VALUE, Long.MAX_VALUE).store(image, "png", root, "1", "a.png", 1L);
        long bytes = Files.size(root.resolve("1/full/a.png")) + Files.size(root.resolve("1/thumb/a.png"));
        WebApplicationException error = assertThrows(WebApplicationException.class,
                () -> quota(bytes * 2 - 1, Long.MAX_VALUE).store(image, "png", root, "2", "b.png", 2L));
        assertEquals(507, error.getResponse().getStatus());
        assertFalse(Files.exists(root.resolve("2")));
    }

    @Test void oldDailyCounterResets() throws Exception {
        Path usage = directory.resolve("upload-usage");
        Files.createDirectories(usage);
        Files.writeString(usage.resolve("1.txt"), "2000-01-01\n999999999");
        quota(Long.MAX_VALUE, 10000).store(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpg",
                directory.resolve("posts"), "1", "a.jpg", 1L);
        assertFalse(Files.readString(usage.resolve("1.txt")).startsWith("2000-01-01"));
    }

    @Test void linksOldThumbnailsWithoutNestingExistingLinks() {
        String image = "<img src=\"/forum/img/42/thumb/a.jpg\">";
        String linked = ForumImageStore.linkThumbnails(image);
        assertTrue(linked.contains("href=\"/forum/img/42/full/a.jpg\""));
        assertEquals(linked, ForumImageStore.linkThumbnails(linked));
        String externalLink = "<a href=\"https://example.com\">" + image + "</a>";
        assertEquals(externalLink, ForumImageStore.linkThumbnails(externalLink));
    }
}
