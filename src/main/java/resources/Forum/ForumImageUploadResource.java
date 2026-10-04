package resources.Forum;

// Explicit JAX-RS imports — avoids ambiguity with java.nio.file.Path
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import model.Users.Roles;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import tools.ForumImageStore;
import tools.SafeImageReader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accepts image uploads from the Quill editor toolbar.
 * Saves full-res + thumbnail into a temporary directory owned by the uploader and
 * returns signed, absolute URLs for immediate display in the editor. The upload is
 * moved to the post when the post/answer that references it is saved.
 *
 * POST /forum/img/upload   multipart/form-data, field: file
 * Returns: { "url": "https://api…/forum/img/tmp_{userId}_{ts}/thumb/img_0.jpg?exp=…&sig=…" }
 */
@Path("/forum/img")
@ApplicationScoped
public class ForumImageUploadResource {

    private static final Logger log = Logger.getLogger(ForumImageUploadResource.class.getName());
    private static final int THUMB_MAX_WIDTH = 400;

    @Inject
    ForumImageStore imageStore;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @POST
    @Path("/upload")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadImage(@jakarta.ws.rs.BeanParam UploadForm form) {
        log.info("ForumImageUploadResource/uploadImage");

        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        if (form == null || form.file == null) {
            return Response.status(400).entity("Kein Bild übermittelt").build();
        }

        if (form.file.size() > 10L * 1024L * 1024L) {
            return Response.status(400).entity("Bild ist zu gross (max. 10 MB)").build();
        }

        // Temp ID before we know the real postId
        String tempId = "tmp_" + userId + "_" + System.currentTimeMillis();

        try {
            File uploaded = form.file.uploadedFile().toFile();
            String originalName = form.file.fileName();
            String requestedExt = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.') + 1)
                : null;
            // Re-encode to a format ImageIO can always write; never trust the client's extension.
            String format = SafeImageReader.outputFormat(requestedExt);

            BufferedImage original = SafeImageReader.read(uploaded, 40_000_000L);
            if (original == null) {
                return Response.status(400).entity("Ungültiges Bildformat").build();
            }

            String filename = "img_0." + format;
            java.nio.file.Path fullDir = imageStore.createDir(tempId, "full");
            java.nio.file.Path thumbDir = imageStore.createDir(tempId, "thumb");
            SafeImageReader.write(original, format, fullDir.resolve(filename));
            SafeImageReader.write(SafeImageReader.scaleToWidth(original, THUMB_MAX_WIDTH, format), format,
                    thumbDir.resolve(filename));

            jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                .add("url", imageStore.signedUrl(tempId, "thumb", filename))
                .add("fullUrl", imageStore.signedUrl(tempId, "full", filename))
                .add("tempId", tempId)
                .build();

            return Response.ok(result).build();

        } catch (Exception e) {
            log.log(Level.SEVERE, "Image upload failed", e);
            return Response.status(500).entity("Fehler beim Verarbeiten des Bildes").build();
        }
    }

    public static class UploadForm {
        @RestForm("file")
        public FileUpload file;
    }
}
