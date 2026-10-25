package tools;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Storage, ownership and URL handling for forum images.
 *
 * <ul>
 *   <li>Editor uploads land in {@code posts/tmp_{userId}_{ts}/} and are only readable by the
 *       uploader. When a post or answer is saved, the uploader's referenced uploads are moved
 *       into {@code posts/{postId}/} so that reads are authorised by that post's category.</li>
 *   <li>Content is stored with canonical, origin-less URLs ({@code /forum/img/{id}/{variant}/{file}}).</li>
 *   <li>When content is returned to an authorised reader, image URLs are rewritten to the public
 *       API origin and carry a short-lived HMAC signature, because {@code <img>} requests cannot
 *       send the bearer token.</li>
 * </ul>
 */
@ApplicationScoped
// Created at startup so that a missing signing key in prod fails the boot, not the first image request.
@io.quarkus.runtime.Startup
public class ForumImageStore {

    public static final String ID_PATTERN = "[0-9]{1,18}|tmp_[0-9]{1,18}_[0-9]{1,18}";
    public static final String FILE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,120}";
    private static final Pattern TMP_ID = Pattern.compile("tmp_([0-9]{1,18})_([0-9]{1,18})");

    /** src/href attribute that points at a forum image (any origin, optional query). */
    private static final Pattern IMAGE_URL_ATTRIBUTE = Pattern.compile(
            "(src|href)=([\"'])(?:https?://[^/\"'\\s>]+)?/forum/img/(" + ID_PATTERN + ")/(full|thumb)/("
                    + FILE_PATTERN + ")(?:\\?[^\"'\\s>]*)?\\2",
            Pattern.CASE_INSENSITIVE);

    /** Any mention of a forum image path, used to decide which files are still in use. */
    private static final Pattern IMAGE_REFERENCE = Pattern.compile(
            "/forum/img/(" + ID_PATTERN + ")/(?:full|thumb)/(" + FILE_PATTERN + ")", Pattern.CASE_INSENSITIVE);
    /** Signed URLs stay valid for 6–12 hours and are stable within a 6 hour window (cacheable). */
    private static final long SIGNATURE_WINDOW_SECONDS = 6L * 60L * 60L;
    /** Longest side of stored full-size images; larger uploads are scaled down. */
    public static final int FULL_MAX_EDGE = 2560;

    @ConfigProperty(name = "forum.images.upload-dir", defaultValue = "${user.home}/wildrovers-uploads/forum")
    String uploadDir;

    @ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:8080")
    String publicApiUrl;

    @ConfigProperty(name = "forum.images.signing-key")
    Optional<String> configuredSigningKey;

    @Inject
    ImageExtractor imageExtractor;

    @Inject
    HtmlSanitizer htmlSanitizer;

    @Inject
    ForumImageQuota imageQuota;

    @Inject
    ForumImageAccess imageAccess;

    /** True in prod: a random key would invalidate all signed URLs on every restart and differ per instance. */
    @ConfigProperty(name = "forum.images.require-signing-key", defaultValue = "false")
    boolean requireSigningKey;

    static final int MIN_SIGNING_KEY_LENGTH = 32;

    private byte[] signingKey;

    @PostConstruct
    void init() {
        String key = configuredSigningKey.map(String::trim).orElse("");
        if (requireSigningKey && key.length() < MIN_SIGNING_KEY_LENGTH) {
            throw new IllegalStateException("forum.images.signing-key (FORUM_IMAGE_SIGNING_KEY) must be set to a stable"
                    + " value of at least " + MIN_SIGNING_KEY_LENGTH + " characters");
        }
        if (!key.isEmpty()) {
            signingKey = sha256(key.getBytes(StandardCharsets.UTF_8));
        } else {
            signingKey = new byte[32];
            new SecureRandom().nextBytes(signingKey);
        }
        while (publicApiUrl.endsWith("/")) {
            publicApiUrl = publicApiUrl.substring(0, publicApiUrl.length() - 1);
        }
    }

    // ── Content preparation (write path) ────────────────────────────────────────

    /**
     * Turns user-submitted HTML into the stored representation: embedded base64 images are
     * extracted (with decoding limits), the HTML is sanitised, image URLs are made canonical and
     * the author's pending editor uploads are attached to the post.
     */
    public String prepareContent(String rawHtml, Long postId, Long authorId) {
        String html = imageExtractor.extractAndSaveImages(rawHtml, postId, authorId);
        html = htmlSanitizer.sanitize(html);
        html = canonicalize(html);
        return attachUploads(html, postId, authorId);
    }

    /** Removes origin and signature from every forum image URL. */
    public String canonicalize(String html) {
        if (html == null || !html.contains("/forum/img/")) return html;
        Matcher m = IMAGE_URL_ATTRIBUTE.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(1) + "=" + m.group(2) + canonicalPath(m.group(3), m.group(4), m.group(5))
                    + m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Moves the author's referenced temporary uploads into the post's directory and rewrites the
     * URLs. Uploads of other users are never moved.
     */
    String attachUploads(String html, Long postId, Long authorId) {
        if (html == null || postId == null || authorId == null || !html.contains("/forum/img/tmp_")) return html;
        Map<String, String> moved = new HashMap<>();
        Matcher m = IMAGE_URL_ATTRIBUTE.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String id = m.group(3);
            Matcher tmp = TMP_ID.matcher(id);
            String replacement = m.group(0);
            if (tmp.matches() && tmp.group(1).equals(String.valueOf(authorId))) {
                String file = m.group(5);
                String key = id + "/" + file;
                String newName = moved.computeIfAbsent(key, k -> moveUpload(id, tmp.group(2), file, postId));
                if (newName != null) {
                    replacement = m.group(1) + "=" + m.group(2)
                            + canonicalPath(String.valueOf(postId), m.group(4), newName) + m.group(2);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String moveUpload(String tmpId, String timestamp, String file, Long postId) {
        String newName = timestamp + "-" + file;
        try {
            Path tmpDir = postsRoot().resolve(tmpId);
            boolean any = false;
            for (String variant : new String[] { "full", "thumb" }) {
                Path source = tmpDir.resolve(variant).resolve(file);
                if (!Files.isRegularFile(source)) continue;
                Path targetDir = createDir(String.valueOf(postId), variant);
                Files.move(source, targetDir.resolve(newName), StandardCopyOption.REPLACE_EXISTING);
                any = true;
            }
            deleteIfEmpty(tmpDir.resolve("full"));
            deleteIfEmpty(tmpDir.resolve("thumb"));
            deleteIfEmpty(tmpDir);
            return any ? newName : null;
        } catch (IOException e) {
            Telemetry.warn("Could not attach upload to post", e, "forum.post.id", postId, "forum.upload.id", tmpId);
            return null;
        }
    }

    private static void deleteIfEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            if (entries.iterator().hasNext()) return;
        }
        Files.deleteIfExists(dir);
    }

    // ── Output (read path) ──────────────────────────────────────────────────────

    /**
     * Rewrites stored post image URLs to absolute, signed URLs for a reader who is allowed to
     * see the content. Temporary uploads are never signed here.
     */
    public String signForOutput(String html) {
        if (html == null || !html.contains("/forum/img/")) return html;
        html = linkThumbnails(html);
        Matcher m = IMAGE_URL_ATTRIBUTE.matcher(html);
        java.util.Set<Long> referencedPosts = new java.util.HashSet<>();
        while (m.find()) {
            if (!m.group(3).startsWith("tmp_")) referencedPosts.add(Long.valueOf(m.group(3)));
        }
        java.util.Set<Long> readablePosts = imageAccess.readablePostIds(referencedPosts);
        m.reset();
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(0);
            if (!m.group(3).startsWith("tmp_") && readablePosts.contains(Long.valueOf(m.group(3)))) {
                replacement = m.group(1) + "=" + m.group(2)
                        + signedUrl(m.group(3), m.group(4), m.group(5)).replace("&", "&amp;") + m.group(2);
            } else {
                // Strip any supplied signature as well: a public post must never redistribute it.
                replacement = m.group(1) + "=" + m.group(2)
                        + canonicalPath(m.group(3), m.group(4), m.group(5)) + m.group(2);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Adds full-size links to uploaded thumbnails, including older stored posts. */
    static String linkThumbnails(String html) {
        Matcher tags = Pattern.compile("<a\\b[^>]*>|</a\\s*>|<img\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        StringBuilder result = new StringBuilder();
        int anchorDepth = 0;
        while (tags.find()) {
            String tag = tags.group();
            String lower = tag.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("</a")) anchorDepth = Math.max(0, anchorDepth - 1);
            else if (lower.startsWith("<a")) anchorDepth++;
            else if (anchorDepth == 0) {
                Matcher url = IMAGE_URL_ATTRIBUTE.matcher(tag);
                if (url.find() && "src".equalsIgnoreCase(url.group(1)) && "thumb".equalsIgnoreCase(url.group(4))) {
                    tag = "<a href=\"" + canonicalPath(url.group(3), "full", url.group(5))
                            + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + tag + "</a>";
                }
            }
            tags.appendReplacement(result, Matcher.quoteReplacement(tag));
        }
        tags.appendTail(result);
        return result.toString();
    }

    public void saveImage(java.awt.image.BufferedImage image, String format, String id, String filename, Long userId)
            throws IOException {
        imageQuota.store(image, format, postsRoot(), id, filename, userId);
    }

    public String signedUrl(String id, String variant, String file) {
        long exp = (System.currentTimeMillis() / 1000L / SIGNATURE_WINDOW_SECONDS + 2) * SIGNATURE_WINDOW_SECONDS;
        String path = canonicalPath(id, variant, file);
        return publicApiUrl + path + "?exp=" + exp + "&sig=" + signature(path, exp);
    }

    public boolean verify(String id, String variant, String file, Long exp, String sig) {
        if (exp == null || sig == null || exp < System.currentTimeMillis() / 1000L) return false;
        byte[] expected = signature(canonicalPath(id, variant, file), exp).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Adds every forum image referenced by {@code html} to {@code into}, keyed by image directory
     * id (post id or tmp id) with the set of referenced file names.
     */
    public static void collectReferences(String html, Map<String, java.util.Set<String>> into) {
        if (html == null || !html.contains("/forum/img/")) return;
        // Deliberately loose (any origin, attribute or quoting): a missed reference deletes a live image.
        Matcher m = IMAGE_REFERENCE.matcher(html);
        while (m.find()) {
            into.computeIfAbsent(m.group(1), k -> new java.util.HashSet<>()).add(m.group(2));
        }
    }

    /** Owner (user id) of a temporary upload directory, or null for post directories. */
    public static Long tmpOwner(String id) {
        Matcher tmp = TMP_ID.matcher(id);
        return tmp.matches() ? Long.valueOf(tmp.group(1)) : null;
    }

    // ── Files ───────────────────────────────────────────────────────────────────

    public Path postsRoot() {
        String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
        return Paths.get(base, "posts").toAbsolutePath().normalize();
    }

    /** Resolves an image file, or null if the path is invalid. */
    public Path resolve(String id, String variant, String file) {
        if (id == null || variant == null || file == null || !id.matches(ID_PATTERN)
                || !("full".equals(variant) || "thumb".equals(variant)) || !file.matches(FILE_PATTERN)
                || file.contains("..")) {
            return null;
        }
        Path root = postsRoot();
        Path requested = root.resolve(id).resolve(variant).resolve(file).normalize();
        return requested.startsWith(root) ? requested : null;
    }

    public Path createDir(String id, String variant) throws IOException {
        Path dir = postsRoot().resolve(id).resolve(variant);
        Files.createDirectories(dir);
        try {
            Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) { /* Windows */ }
        return dir;
    }

    public static String canonicalPath(String id, String variant, String file) {
        return "/forum/img/" + id + "/" + variant + "/" + file;
    }

    private String signature(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal((path + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
