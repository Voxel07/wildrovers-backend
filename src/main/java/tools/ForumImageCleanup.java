package tools;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Removes forum image files that no post or answer references any more: images of deleted
 * posts/topics/categories/users, images edited out of a post, and editor uploads that were never
 * attached to a post. A file is only deleted when no stored content mentions its path. Running as a sweep covers every deletion path (including native SQL bulk
 * deletes) without coupling file I/O to database transactions.
 */
@ApplicationScoped
public class ForumImageCleanup {
    private static final Logger log = Logger.getLogger(ForumImageCleanup.class.getName());

    /** Files younger than this are kept: their post/answer may not be committed yet. */
    static final Duration MIN_AGE = Duration.ofHours(1);
    /** Unattached editor uploads are kept this long so drafts can still be saved. */
    static final Duration TMP_MAX_AGE = Duration.ofHours(24);

    @Inject
    ForumImageStore imageStore;

    @Inject
    EntityManager em;

    @Scheduled(cron = "${forum.images.cleanup-cron:0 30 3 * * ?}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledCleanup() {
        try {
            int removed = cleanup(Instant.now());
            log.info("Forum image cleanup removed " + removed + " files");
        } catch (Exception e) {
            log.log(Level.WARNING, "Forum image cleanup failed", e);
        }
    }

    /** Runs one sweep and returns the number of deleted files. */
    @Transactional
    public int cleanup(Instant now) throws IOException {
        Path root = imageStore.postsRoot();
        if (!Files.isDirectory(root)) return 0;

        Map<String, Set<String>> referenced = new HashMap<>();
        collect("SELECT p.content FROM ForumPost p WHERE p.content LIKE '%/forum/img/%'", referenced);
        collect("SELECT a.content FROM ForumAnswer a WHERE a.content LIKE '%/forum/img/%'", referenced);

        int removed = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) continue;
                String id = dir.getFileName().toString();
                if (!id.matches(ForumImageStore.ID_PATTERN)) continue;
                // Older posts reference tmp_* uploads directly, so tmp files are kept while referenced.
                Duration minAge = id.startsWith("tmp_") ? TMP_MAX_AGE : MIN_AGE;
                Set<String> keep = referenced.getOrDefault(id, Set.of());
                for (String variant : new String[] { "full", "thumb" }) {
                    Path variantDir = dir.resolve(variant);
                    if (!Files.isDirectory(variantDir)) continue;
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(variantDir)) {
                        for (Path file : files) {
                            if (Files.isRegularFile(file) && !keep.contains(file.getFileName().toString())
                                    && olderThan(file, now, minAge)) {
                                Files.deleteIfExists(file);
                                removed++;
                            }
                        }
                    }
                    deleteIfEmpty(variantDir);
                }
                deleteIfEmpty(dir);
            }
        }
        return removed;
    }

    private void collect(String jpql, Map<String, Set<String>> into) {
        List<String> contents = em.createQuery(jpql, String.class).getResultList();
        for (String html : contents) {
            ForumImageStore.collectReferences(html, into);
        }
    }

    /** True when the path and everything below it were last modified before {@code now - age}. */
    private static boolean olderThan(Path path, Instant now, Duration age) throws IOException {
        Instant limit = now.minus(age);
        try (Stream<Path> paths = Files.walk(path)) {
            return paths.allMatch(p -> {
                try {
                    return Files.getLastModifiedTime(p).toInstant().isBefore(limit);
                } catch (IOException e) {
                    return false;
                }
            });
        }
    }

    private static void deleteIfEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            if (entries.iterator().hasNext()) return;
        }
        Files.deleteIfExists(dir);
    }
}
