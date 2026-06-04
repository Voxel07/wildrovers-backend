package tools;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts base64-encoded images from HTML content, saves them as files,
 * generates thumbnails, and replaces data: URIs with server URLs.
 *
 * Upload layout:
 *   {uploadDir}/posts/{postId}/full/img_{n}.webp    — full resolution
 *   {uploadDir}/posts/{postId}/thumb/img_{n}.webp   — max 400 px wide thumbnail
 *
 * The upload directory must have no execute permissions in production.
 * On POSIX systems this is enforced automatically; on Windows, configure the
 * folder ACL to deny execution at the OS level.
 */
@ApplicationScoped
public class ImageExtractor {

    private static final Logger log = Logger.getLogger(ImageExtractor.class.getName());

    /** Max thumbnail width (px). Height is scaled proportionally. */
    private static final int THUMB_MAX_WIDTH = 400;

    /** Pattern matching <img src="data:image/TYPE;base64,DATA"> */
    private static final Pattern BASE64_IMG_PATTERN =
        Pattern.compile("<img[^>]+src=\"(data:image/([a-zA-Z+]+);base64,([^\"]+))\"[^>]*>",
                        Pattern.CASE_INSENSITIVE);

    @ConfigProperty(name = "forum.images.upload-dir",
                    defaultValue = "${user.home}/wildrovers-uploads/forum")
    String uploadDir;

    /**
     * Scans HTML for base64 images, saves them to disk, and returns the
     * content with data: URIs replaced by server URLs.
     *
     * @param html    post/answer HTML content
     * @param postId  used to build the storage path and URL
     * @param baseUrl public URL base, e.g. "http://localhost:8080"
     * @return HTML with all data: image references replaced by /forum/img/... URLs
     */
    public String extractAndSaveImages(String html, Long postId, String baseUrl) {
        if (html == null || postId == null || !html.contains("data:image")) {
            return html;
        }

        Path fullDir  = resolveAndCreate(postId, "full");
        Path thumbDir = resolveAndCreate(postId, "thumb");
        if (fullDir == null || thumbDir == null) return html;

        Matcher matcher = BASE64_IMG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        int imgIndex = 0;

        while (matcher.find()) {
            String dataUri  = matcher.group(1);
            String mimeType = matcher.group(2).toLowerCase(Locale.ROOT); // e.g. "png"
            String b64data  = matcher.group(3);

            String ext = mimeType.equals("jpeg") ? "jpg" : mimeType;
            String filename = "img_" + imgIndex + "." + ext;

            try {
                byte[] imageBytes = Base64.getDecoder().decode(b64data);
                BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));

                if (original == null) {
                    log.warning("Could not decode image " + imgIndex + " for post " + postId);
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                    imgIndex++;
                    continue;
                }

                // Save full-resolution
                Path fullPath = fullDir.resolve(filename);
                saveWithPermissions(original, ext, fullPath);

                // Save thumbnail
                BufferedImage thumb = scaleTo(original, THUMB_MAX_WIDTH);
                Path thumbPath = thumbDir.resolve(filename);
                saveWithPermissions(thumb, ext, thumbPath);

                // Build replacement: thumbnail shown, full-res on click
                String fullUrl  = baseUrl + "/forum/img/" + postId + "/full/" + filename;
                String thumbUrl = baseUrl + "/forum/img/" + postId + "/thumb/" + filename;
                String replacement = "<a href=\"" + fullUrl + "\" target=\"_blank\">"
                    + "<img src=\"" + thumbUrl + "\" alt=\"Beitragsbild\" style=\"max-width:100%;height:auto;\">"
                    + "</a>";

                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                log.info("Saved post " + postId + " image " + imgIndex);
                imgIndex++;

            } catch (Exception e) {
                log.log(Level.SEVERE, "Error processing image " + imgIndex + " for post " + postId, e);
                // Leave the original data: URI in place on error
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                imgIndex++;
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Scale image proportionally so width <= maxWidth. */
    private BufferedImage scaleTo(BufferedImage src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return src;
        double ratio  = (double) maxWidth / src.getWidth();
        int newHeight = (int) Math.round(src.getHeight() * ratio);
        BufferedImage scaled = new BufferedImage(maxWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, maxWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    /** Save a BufferedImage to disk and try to set non-executable permissions. */
    private void saveWithPermissions(BufferedImage img, String format, Path path) throws IOException {
        ImageIO.write(img, format, path.toFile());
        try {
            Files.setPosixFilePermissions(path,
                PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException ignored) {
            // Windows — permissions must be set at the folder level externally
        }
    }

    /** Resolve and create the subdirectory, returning null on failure. */
    private Path resolveAndCreate(Long postId, String subDir) {
        // Expand ${user.home} if present
        String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
        Path dir = Paths.get(base, "posts", String.valueOf(postId), subDir);
        try {
            Files.createDirectories(dir);
            try {
                Files.setPosixFilePermissions(dir,
                    PosixFilePermissions.fromString("rwxr-xr-x"));
            } catch (UnsupportedOperationException ignored) { /* Windows */ }
            return dir;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Cannot create upload dir " + dir, e);
            return null;
        }
    }
}
