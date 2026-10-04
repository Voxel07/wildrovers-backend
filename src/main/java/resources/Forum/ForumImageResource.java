package resources.Forum;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import model.User;
import model.Users.Roles;
import tools.ForumImageStore;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Serves uploaded forum images (full resolution and thumbnails).
 *
 * GET /forum/img/{postId}/full/{filename}   → full-resolution image
 * GET /forum/img/{postId}/thumb/{filename}  → thumbnail
 *
 * Images of posts in categories visible to visitors are public. All other images
 * require either a valid signed URL (issued together with content the reader was
 * authorised to see) or a bearer token whose account may read the post's category.
 * Temporary editor uploads are readable only by their uploader.
 */
@Path("/forum/img")
@ApplicationScoped
public class ForumImageResource {

    private static final Logger log = Logger.getLogger(ForumImageResource.class.getName());

    @Inject
    ForumImageStore imageStore;

    @Inject
    EntityManager em;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @GET
    @Path("/{postId}/{variant}/{filename}")
    @PermitAll
    @Produces("image/*")
    public Response getImage(
            @PathParam("postId") String postId,
            @PathParam("variant") String variant,
            @PathParam("filename") String filename,
            @QueryParam("exp") Long exp,
            @QueryParam("sig") String sig) {

        java.nio.file.Path requested = imageStore.resolve(postId, variant, filename);
        if (requested == null) {
            return Response.status(400).entity("Ungültiger Pfad").build();
        }

        boolean signed = imageStore.verify(postId, variant, filename, exp, sig);
        String cacheControl;
        Long tmpOwner = ForumImageStore.tmpOwner(postId);
        if (tmpOwner != null) {
            User user = currentUser();
            if (!signed && (user == null || !tmpOwner.equals(user.getId()))) {
                return Response.status(404).entity("Bild nicht gefunden").build();
            }
            cacheControl = "private, max-age=300";
        } else {
            List<String> visibility = em.createQuery(
                    "SELECT c.visibility FROM ForumPost p JOIN p.topic t JOIN t.category c WHERE p.id = :id",
                    String.class)
                    .setParameter("id", Long.valueOf(postId))
                    .getResultList();
            if (visibility.isEmpty()) {
                // Unknown or deleted post: never serve its files.
                return Response.status(404).entity("Bild nicht gefunden").build();
            }
            String required = visibility.get(0) == null || visibility.get(0).isBlank()
                    ? Roles.VSISITOR : visibility.get(0);
            if (Roles.VSISITOR.equals(required)) {
                cacheControl = "public, max-age=86400";
            } else {
                if (!signed) {
                    User user = currentUser();
                    if (user == null || !Roles.hasRequiredRole(user.getRole(), required)) {
                        log.info("Denied forum image read for post " + postId);
                        return Response.status(404).entity("Bild nicht gefunden").build();
                    }
                }
                cacheControl = "private, max-age=3600";
            }
        }

        File file = requested.toFile();
        if (!file.isFile()) {
            return Response.status(404).entity("Bild nicht gefunden").build();
        }

        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        String mediaType = switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"         -> "image/png";
            case "gif"         -> "image/gif";
            case "webp"        -> "image/webp";
            default            -> "application/octet-stream";
        };

        return Response.ok(file, mediaType)
            .header("Cache-Control", cacheControl)
            .header("Vary", "Authorization")
            .header("X-Content-Type-Options", "nosniff")
            .build();
    }

    private User currentUser() {
        try {
            return userPrincipalResolver.resolveUser();
        } catch (WebApplicationException e) {
            return null; // blocked/disabled accounts are treated as anonymous readers
        }
    }
}
