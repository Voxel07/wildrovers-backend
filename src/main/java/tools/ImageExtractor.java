package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.opentelemetry.instrumentation.annotations.WithSpan;

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
    @WithSpan
    public String extractAndSaveImages(String html, Long postId, Long authorId) {
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
                Telemetry.warn("Dropping embedded image beyond the limit", "forum.post.id", postId, "image.index", imgIndex, "image.limit", MAX_IMAGES);
            } else if (b64data.length() > MAX_BASE64_CHARS) {
                Telemetry.warn("Dropping oversized embedded image", "forum.post.id", postId, "image.index", imgIndex, "image.base64_length", b64data.length());
            } else {
                try {
                    byte[] imageBytes = Base64.getMimeDecoder().decode(b64data);
                    BufferedImage original = imageBytes.length <= MAX_IMAGE_BYTES
                            ? SafeImageReader.readOriented(imageBytes, MAX_PIXELS, ForumImageStore.FULL_MAX_EDGE, format) : null;
                    if (original == null) {
                        Telemetry.warn("Could not decode embedded image", "forum.post.id", postId, "image.index", imgIndex, "image.format", format);
                    } else {
                        String filename = "b64-" + UUID.randomUUID() + "." + format;
                        imageStore.saveImage(original, format, String.valueOf(postId), filename, authorId);

                        String fullUrl = ForumImageStore.canonicalPath(String.valueOf(postId), "full", filename);
                        String thumbUrl = ForumImageStore.canonicalPath(String.valueOf(postId), "thumb", filename);
                        replacement = "<a href=\"" + fullUrl + "\" target=\"_blank\">"
                            + "<img src=\"" + thumbUrl + "\" alt=\"Beitragsbild\" style=\"max-width:100%;height:auto;\">"
                            + "</a>";
                        Telemetry.event("image.saved", "forum.post.id", postId, "image.index", imgIndex, "image.format", format,
                                "image.width", original.getWidth(), "image.height", original.getHeight());
                    }
                } catch (jakarta.ws.rs.WebApplicationException e) {
                    throw e;
                } catch (Exception e) {
                    Telemetry.warn("Rejected embedded image", e, "forum.post.id", postId, "image.index", imgIndex);
                }
            }
            imgIndex++;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
