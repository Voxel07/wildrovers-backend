package resources.Forum;

// Explicit JAX-RS imports — avoids ambiguity with java.nio.file.Path
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
// Use fully-qualified java.nio.file.Path in signatures to avoid clash with @Path annotation
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accepts image uploads from the Quill editor toolbar.
 * Saves full-res + thumbnail, returns the thumbnail URL for immediate display.
 *
 * POST /forum/img/upload   multipart/form-data, field: file
 * Returns: { "url": "/forum/img/tmp_{userId}_{ts}/thumb/img_0.jpg" }
 */
@Path("/forum/img")
@ApplicationScoped
public class ForumImageUploadResource {

    private static final Logger log = Logger.getLogger(ForumImageUploadResource.class.getName());
    private static final int THUMB_MAX_WIDTH = 400;

    @ConfigProperty(name = "forum.images.upload-dir",
                    defaultValue = "${user.home}/wildrovers-uploads/forum")
    String uploadDir;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadImage(@org.jboss.resteasy.reactive.MultipartForm UploadForm form) {
        log.info("ForumImageUploadResource/uploadImage");

        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        if (form == null || form.file == null) {
            return Response.status(400).entity("Kein Bild übermittelt").build();
        }

        // Temp ID before we know the real postId
        String tempId = "tmp_" + userId + "_" + System.currentTimeMillis();
        String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));

        java.nio.file.Path fullDir  = createDir(base, tempId, "full");
        java.nio.file.Path thumbDir = createDir(base, tempId, "thumb");
        if (fullDir == null || thumbDir == null) {
            return Response.status(500).entity("Fehler beim Erstellen des Upload-Verzeichnisses").build();
        }

        try {
            File uploaded = form.file.uploadedFile().toFile();
            String originalName = form.file.fileName();
            String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";

            BufferedImage original = ImageIO.read(uploaded);
            if (original == null) {
                return Response.status(400).entity("Ungültiges Bildformat").build();
            }

            String filename = "img_0." + ext;

            // Save full-res
            java.nio.file.Path fullPath = fullDir.resolve(filename);
            writeWithPermissions(original, ext, fullPath);

            // Save thumbnail
            BufferedImage thumb = scaleTo(original, THUMB_MAX_WIDTH);
            java.nio.file.Path thumbPath = thumbDir.resolve(filename);
            writeWithPermissions(thumb, ext, thumbPath);

            String thumbUrl = "/forum/img/" + tempId + "/thumb/" + filename;

            jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                .add("url", thumbUrl)
                .add("fullUrl", "/forum/img/" + tempId + "/full/" + filename)
                .add("tempId", tempId)
                .build();

            return Response.ok(result).build();

        } catch (Exception e) {
            log.log(Level.SEVERE, "Image upload failed", e);
            return Response.status(500).entity("Fehler beim Verarbeiten des Bildes").build();
        }
    }

    private java.nio.file.Path createDir(String base, String id, String variant) {
        java.nio.file.Path dir = Paths.get(base, "posts", id, variant);
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

    private void writeWithPermissions(BufferedImage img, String format, java.nio.file.Path path) throws IOException {
        ImageIO.write(img, format, path.toFile());
        try {
            Files.setPosixFilePermissions(path,
                PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException ignored) { /* Windows */ }
    }

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

    public static class UploadForm {
        @RestForm("file")
        public FileUpload file;
    }
}
