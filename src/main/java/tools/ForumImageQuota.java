package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Disk-backed limits shared by editor and base64 uploads, retained across restarts. */
@ApplicationScoped
public class ForumImageQuota {
    @ConfigProperty(name = "forum.images.max-storage-bytes", defaultValue = "2147483648")
    long maxStorageBytes;

    @ConfigProperty(name = "forum.images.max-user-daily-bytes", defaultValue = "52428800")
    long maxUserDailyBytes;

    public synchronized void store(BufferedImage image, String format, Path root, String id, String filename, Long userId)
            throws IOException {
        if (userId == null || userId <= 0 || !id.matches(ForumImageStore.ID_PATTERN)
                || !filename.matches(ForumImageStore.FILE_PATTERN)) throw new IOException("Invalid image owner or path");
        // Encode outside the upload volume so rejected files never exceed its storage budget.
        Path full = Files.createTempFile("forum-full-", "." + format);
        Path thumb = Files.createTempFile("forum-thumb-", "." + format);
        try {
            SafeImageReader.write(image, format, full);
            SafeImageReader.write(SafeImageReader.scaleToWidth(image, 400, format), format, thumb);
            long bytes = Files.size(full) + Files.size(thumb);
            Path usageDir = root.getParent().resolve("upload-usage");
            Files.createDirectories(usageDir);
            // Also serialises writers in other backend processes sharing the same upload volume.
            try (FileChannel channel = FileChannel.open(usageDir.resolve("quota.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                Path counter = usageDir.resolve(userId + ".txt");
                String day = LocalDate.now(ZoneOffset.UTC).toString();
                long daily = 0;
                if (Files.exists(counter)) {
                    String[] stored = Files.readString(counter).split("\n");
                    if (stored.length != 2) throw new IOException("Invalid upload usage counter");
                    if (stored[0].equals(day)) daily = Long.parseLong(stored[1]);
                }
                if (maxUserDailyBytes <= 0 || bytes > maxUserDailyBytes - daily) {
                    throw new WebApplicationException(Response.status(429)
                            .entity("Das tägliche Bilderlimit wurde erreicht.").build());
                }
                long used = 0;
                if (Files.isDirectory(root)) {
                    try (var paths = Files.walk(root)) {
                        for (Path file : (Iterable<Path>) paths::iterator) {
                            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                                try { used += Files.size(file); }
                                catch (java.nio.file.NoSuchFileException ignored) { /* nightly cleanup */ }
                            }
                        }
                    }
                }
                if (maxStorageBytes <= 0 || bytes > maxStorageBytes - used) {
                    throw new WebApplicationException(Response.status(507)
                            .entity("Der Bildspeicher ist voll. Bitte die Administration kontaktieren.").build());
                }
                Path fullTarget = root.resolve(id).resolve("full").resolve(filename);
                Path thumbTarget = root.resolve(id).resolve("thumb").resolve(filename);
                if (Files.exists(fullTarget) || Files.exists(thumbTarget)) {
                    throw new java.nio.file.FileAlreadyExistsException(filename);
                }
                // Reserve before copying; failed writes conservatively count towards today's limit.
                Files.writeString(counter, day + "\n" + (daily + bytes));
                Files.createDirectories(fullTarget.getParent());
                Files.createDirectories(thumbTarget.getParent());
                try {
                    Files.copy(full, fullTarget);
                    Files.copy(thumb, thumbTarget);
                } catch (IOException e) {
                    Files.deleteIfExists(fullTarget);
                    Files.deleteIfExists(thumbTarget);
                    throw e;
                }
            }
        } finally {
            Files.deleteIfExists(full);
            Files.deleteIfExists(thumb);
        }
    }
}
