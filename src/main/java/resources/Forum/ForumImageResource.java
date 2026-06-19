package resources.Forum;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Serves uploaded forum images (full resolution and thumbnails).
 * Files are served from outside the classpath, from a non-executable directory.
 *
 * GET /forum/img/{postId}/full/{filename}   → full-resolution image
 * GET /forum/img/{postId}/thumb/{filename}  → thumbnail
 */
@Path("/forum/img")
@ApplicationScoped
public class ForumImageResource {

    private static final Logger log = Logger.getLogger(ForumImageResource.class.getName());

    @ConfigProperty(name = "forum.images.upload-dir",
                    defaultValue = "${user.home}/wildrovers-uploads/forum")
    String uploadDir;

    @GET
    @Path("/{postId}/{variant}/{filename}")
    @PermitAll
    @Produces("image/*")
    public Response getImage(
            @PathParam("postId") String postId,
            @PathParam("variant") String variant,
            @PathParam("filename") String filename) {

        log.info("ForumImageResource/getImage postId=" + postId
                 + " variant=" + variant + " filename=" + filename);

        // Validate variant to prevent path traversal
        if (!"full".equals(variant) && !"thumb".equals(variant)) {
            return Response.status(400).entity("Ungültige Variante").build();
        }
        // Prevent directory traversal in filename
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response.status(400).entity("Ungültiger Dateiname").build();
        }

        String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
        File file = Paths.get(base, "posts", String.valueOf(postId), variant, filename).toFile();

        if (!file.exists() || !file.isFile()) {
            return Response.status(404).entity("Bild nicht gefunden").build();
        }

        // Derive media type from extension
        String ext = filename.contains(".")
            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
            : "octet-stream";
        String mediaType = switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"         -> "image/png";
            case "gif"         -> "image/gif";
            case "webp"        -> "image/webp";
            default            -> "application/octet-stream";
        };

        return Response.ok(file, mediaType)
            .header("Cache-Control", "public, max-age=86400")
            .build();
    }
}
