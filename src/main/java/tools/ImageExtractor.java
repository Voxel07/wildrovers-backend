package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts base64-encoded images from HTML content, saves them as files,
 * generates thumbnails, and replaces data: URIs with canonical image URLs.
 *
 * Upload layout:
 *   {uploadDir}/posts/{postId}/full/b64-{random}.{png|jpg}  — full resolution
 *   {uploadDir}/posts/{postId}/thumb/b64-{random}.{png|jpg} — max 400 px wide thumbnail
 *
 * Decoding is bounded: at most {@value #MAX_IMAGES} images per document, at most
 * {@value #MAX_IMAGE_BYTES} decoded bytes per image and {@value #MAX_PIXELS} pixels,
 * checked from the image header before any pixel data is allocated.
 */
@ApplicationScoped
public class ImageExtractor {

    private static final Logger log = Logger.getLogger(ImageExtractor.class.getName());

    /** Max thumbnail width (px). Height is scaled proportionally. */
    private static final int THUMB_MAX_WIDTH = 400;
    static final int MAX_IMAGES = 10;
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    static final long MAX_PIXELS = 25_000_000L;
    /** Base64 inflates by 4/3; reject oversized payloads before decoding them. */
    private static final int MAX_BASE64_CHARS = (MAX_IMAGE_BYTES / 3 + 1) * 4;

    /** Pattern matching <img src="data:image/TYPE;base64,DATA"> */
    private static final Pattern BASE64_IMG_PATTERN =
        Pattern.compile("<img[^>]+src=\"(data:image/([a-zA-Z+]+);base64,([^\"]+))\"[^>]*>",
                        Pattern.CASE_INSENSITIVE);

    @Inject
    ForumImageStore imageStore;

    /**
     * Scans HTML for base64 images, saves them to disk, and returns the content with
     * data: URIs replaced by canonical /forum/img/... URLs. Images that exceed the
     * limits or cannot be decoded are removed.
     */
    public String extractAndSaveImages(String html, Long postId) {
        if (html == null || postId == null || !html.contains("data:image")) {
            return html;
        }

        Matcher matcher = BASE64_IMG_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        int imgIndex = 0;

        while (matcher.find()) {
            String b64data = matcher.group(3);
            String format = SafeImageReader.outputFormat(matcher.group(2));
            String replacement = "";
            if (imgIndex >= MAX_IMAGES) {
                log.warning("Post " + postId + ": dropping embedded image beyond the limit of " + MAX_IMAGES);
            } else if (b64data.length() > MAX_BASE64_CHARS) {
                log.warning("Post " + postId + ": dropping oversized embedded image " + imgIndex);
            } else {
                try {
                    byte[] imageBytes = Base64.getMimeDecoder().decode(b64data);
                    BufferedImage original = imageBytes.length <= MAX_IMAGE_BYTES
                            ? SafeImageReader.read(imageBytes, MAX_PIXELS) : null;
                    if (original == null) {
                        log.warning("Could not decode embedded image " + imgIndex + " for post " + postId);
                    } else {
                        String filename = "b64-" + UUID.randomUUID() + "." + format;
                        Path fullDir = imageStore.createDir(String.valueOf(postId), "full");
                        Path thumbDir = imageStore.createDir(String.valueOf(postId), "thumb");
                        SafeImageReader.write(original, format, fullDir.resolve(filename));
                        SafeImageReader.write(SafeImageReader.scaleToWidth(original, THUMB_MAX_WIDTH, format), format,
                                thumbDir.resolve(filename));

                        String fullUrl = ForumImageStore.canonicalPath(String.valueOf(postId), "full", filename);
                        String thumbUrl = ForumImageStore.canonicalPath(String.valueOf(postId), "thumb", filename);
                        replacement = "<a href=\"" + fullUrl + "\" target=\"_blank\">"
                            + "<img src=\"" + thumbUrl + "\" alt=\"Beitragsbild\" style=\"max-width:100%;height:auto;\">"
                            + "</a>";
                        log.info("Saved post " + postId + " image " + imgIndex);
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Rejected embedded image " + imgIndex + " for post " + postId, e);
                }
            }
            imgIndex++;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
