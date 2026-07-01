package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

//Logging zeug
import java.util.logging.Logger;

//HTTP Requests
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import model.User;
import orm.UserOrm;
import orm.Forum.ForumPostOrm;
import tools.SignupSettings;
import tools.AuditLogger;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;

//Sicherheits Zeug
// Validator
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;

@Path("/user")
@RequestScoped
public class UserResouce {
    private static final Logger log = Logger.getLogger(UserResouce.class.getName());

    @Inject
    UserOrm userOrm;

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    SignupSettings signupSettings;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    tools.HtmlSanitizer htmlSanitizer;

    @ConfigProperty(name = "user.photos.upload-dir", defaultValue = "${user.home}/wildrovers-uploads/user-photos")
    String uploadDir;

    @ConfigProperty(name = "user.backgrounds.upload-dir", defaultValue = "${user.home}/wildrovers-uploads/profile-backgrounds")
    String backgroundUploadDir;



    @Inject
    Validator validator;

    @GET
    @Path("/me")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMe() {
        log.info("UserResource/getMe");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Benutzer nicht eingeloggt oder unbekannt").build();
        }
        if (user.getIsBlocked()) {
            return Response.status(403).entity("Dein Account wurde gesperrt.").build();
        }
        user.setEventsAttended(userOrm.getEventsAttendedCount(user.getId()));
        return Response.ok(user).build();
    }

    @GET
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<User> getUser(@QueryParam("userId") Long userId, @QueryParam("username") String userName) {
        log.info("UserResource/getUser");
        if (userId != null) {
            log.info("getUserById");
            return userOrm.getUserById(userId);
        } else if (userName != null) {
            log.info("getUserByUsername");
            return userOrm.getUserByUsername(userName);
        } else {
            log.info("getUsers");
            return userOrm.getUsers();
        }
    }

    @POST
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateUser(User user) {
        log.info("UserResource/updateUser");
        User admin = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, admin != null ? admin.getUserName() : "unknown",
                admin != null ? admin.getId() : null,
                "UPDATE", "User", user.getId(), "userName=" + user.getUserName());
        return userOrm.updateUser(user);
    }

    @POST
    @Path("/login")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(User user) {
        log.info("UserResource/login");
        return userOrm.loginUser(user);
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response logout() {
        log.info("UserResource/logout");
        return userOrm.logoutUser();
    }

    @PUT
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addUser(@Valid User usr) {
        log.info("UserResource/addUser");
        if (!signupSettings.isSignupEnabled()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Die Registrierung ist derzeit deaktiviert.")
                    .build();
        }
        AuditLogger.crud(log, usr.getUserName(), null, "REGISTER", "User",
                usr.getUserName());
        return userOrm.addUser(usr);
    }

    /**
     * Admin-only: delete all forum posts created by a specific user.
     * This is a separate step from deleting the user account itself,
     * so admins can clean up spam posts before or during account removal.
     */
    @DELETE
    @Path("/{userId}/posts")
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAllUserPosts(@PathParam("userId") Long userId) {
        log.info("UserResource/deleteAllUserPosts: " + userId);
        try {
            String result = forumPostOrm.deleteAllPostsFromUser(userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            log.log(java.util.logging.Level.SEVERE, "Error deleting posts for user " + userId, e);
            return Response.status(500).entity("Fehler beim Löschen der Forumsbeiträge: " + e.getMessage()).build();
        }
    }

    /**
     * Public endpoint: returns whether new user registrations are currently allowed.
     */
    @GET
    @Path("/signup-status")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSignupStatus() {
        log.info("UserResource/getSignupStatus");
        jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                .add("signupEnabled", signupSettings.isSignupEnabled())
                .build();
        return Response.ok(result).build();
    }

    /**
     * Admin-only: enable or disable new user registrations at runtime.
     */
    @POST
    @Path("/signup-enabled")
    @RolesAllowed({ "Admin", "Vorstand" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setSignupEnabled(@QueryParam("enabled") boolean enabled) {
        log.info("UserResource/setSignupEnabled: " + enabled);
        User admin = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, admin != null ? admin.getUserName() : "unknown",
                admin != null ? admin.getId() : null,
                "TOGGLE", "SignupSettings", null, "enabled=" + enabled);
        signupSettings.setSignupEnabled(enabled);
        jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                .add("signupEnabled", signupSettings.isSignupEnabled())
                .build();
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{userId}")
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(
            @PathParam("userId") Long userId,
            @QueryParam("deleteAccount") @DefaultValue("true") boolean deleteAccount,
            @QueryParam("deleteEvents") @DefaultValue("true") boolean deleteEvents,
            @QueryParam("deletePosts") @DefaultValue("false") boolean deletePosts,
            @QueryParam("deleteGallery") @DefaultValue("true") boolean deleteGallery,
            @QueryParam("hardDelete") @DefaultValue("false") boolean hardDelete) {
        log.info("UserResource/deleteUser: " + userId
            + " (account=" + deleteAccount
            + ", events=" + deleteEvents
            + ", posts=" + deletePosts
            + ", gallery=" + deleteGallery
            + ", hardDelete=" + hardDelete + ")");
        User admin = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, admin != null ? admin.getUserName() : "unknown",
                admin != null ? admin.getId() : null,
                "DELETE", "User", userId,
                "hardDelete=" + hardDelete + " events=" + deleteEvents + " posts=" + deletePosts + " gallery=" + deleteGallery);
        return userOrm.deleteUserWithOptions(userId, deleteAccount, deleteEvents, deletePosts, deleteGallery, hardDelete);
    }

    @POST
    @Path("/me/profile")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateProfile(User profileData) {
        log.info("UserResource/updateProfile");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Benutzer nicht eingeloggt").build();
        }
        AuditLogger.crud(log, user.getUserName(), user.getId(), "UPDATE", "Profile",
                user.getId());
        String phrase = null;
        if (profileData.getPhrase() != null) {
            phrase = htmlSanitizer.sanitizeTitle(profileData.getPhrase());
        }
        try {
            userOrm.updateUserProfile(user.getId(), phrase, profileData.getBirthday(), profileData.getFirstName(),
                    profileData.getLastName(), profileData.getEmail());
            return Response.ok(userOrm.getUserById(user.getId()).get(0)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/me/photo")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadPhoto(@jakarta.ws.rs.BeanParam PhotoUploadForm form) {
        log.info("UserResource/uploadPhoto");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        if (form == null || form.file == null) {
            return Response.status(400).entity("Keine Datei übermittelt").build();
        }
        if (form.file.size() > 2 * 1024 * 1024) {
            return Response.status(400).entity("Datei ist zu groß (max. 2MB)").build();
        }

        try {
            File uploadedFile = form.file.uploadedFile().toFile();
            BufferedImage original = ImageIO.read(uploadedFile);
            if (original == null) {
                return Response.status(400).entity("Ungültiges Bildformat").build();
            }

            BufferedImage scaled = scaleImage(original, 400);

            String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
            java.nio.file.Path dir = Paths.get(base);
            Files.createDirectories(dir);
            java.nio.file.Path destPath = dir.resolve(user.getId() + ".jpg");

            ImageIO.write(scaled, "jpg", destPath.toFile());

            String photoUrl = "/user/photo/" + user.getId();
            userOrm.updateUserPhotoUrl(user.getId(), photoUrl);

            jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                    .add("photoUrl", photoUrl)
                    .build();
            return Response.ok(result).build();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to upload photo", e);
            return Response.status(500).entity("Fehler beim Verarbeiten des Fotos").build();
        }
    }

    @POST
    @Path("/me/background")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadBackground(@jakarta.ws.rs.BeanParam PhotoUploadForm form) {
        log.info("UserResource/uploadBackground");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        if (form == null || form.file == null) {
            return Response.status(400).entity("Keine Datei übermittelt").build();
        }
        if (form.file.size() > 5 * 1024 * 1024) {
            return Response.status(400).entity("Datei ist zu groß (max. 5MB)").build();
        }

        try {
            File uploadedFile = form.file.uploadedFile().toFile();
            BufferedImage original = ImageIO.read(uploadedFile);
            if (original == null) {
                return Response.status(400).entity("Ungültiges Bildformat").build();
            }

            BufferedImage scaled = scaleImage(original, 1200);

            String base = backgroundUploadDir.replace("${user.home}", System.getProperty("user.home"));
            java.nio.file.Path dir = Paths.get(base);
            Files.createDirectories(dir);
            java.nio.file.Path destPath = dir.resolve(user.getId() + "_background.jpg");

            ImageIO.write(scaled, "jpg", destPath.toFile());

            String backgroundUrl = "/user/background/" + user.getId() + "?v=" + System.currentTimeMillis();
            userOrm.updateUserBackgroundUrl(user.getId(), backgroundUrl);

            jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                    .add("backgroundUrl", backgroundUrl)
                    .build();
            return Response.ok(result).build();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to upload background", e);
            return Response.status(500).entity("Fehler beim Verarbeiten des Hintergrundbildes").build();
        }
    }

    @DELETE
    @Path("/me/background")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBackground() {
        log.info("UserResource/deleteBackground");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        try {
            String base = backgroundUploadDir.replace("${user.home}", System.getProperty("user.home"));
            java.nio.file.Path filePath = Paths.get(base, user.getId() + "_background.jpg");
            File file = filePath.toFile();
            if (file.exists() && file.isFile()) {
                Files.deleteIfExists(filePath);
            }
            userOrm.updateUserBackgroundUrl(user.getId(), null);

            return Response.ok(jakarta.json.Json.createObjectBuilder()
                    .add("message", "Hintergrundbild gelöscht")
                    .build()).build();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to delete background", e);
            return Response.status(500).entity("Fehler beim Löschen des Hintergrundbildes").build();
        }
    }

    @GET
    @Path("/photo/{userId}")
    @PermitAll
    @Produces("image/jpeg")
    public Response getPhoto(@PathParam("userId") Long userId) {
        log.info("UserResource/getPhoto: " + userId);
        String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
        File file = Paths.get(base, userId + ".jpg").toFile();
        if (!file.exists() || !file.isFile()) {
            return Response.status(404).entity("Photo nicht gefunden").build();
        }
        return Response.ok(file)
                .header("Cache-Control", "public, max-age=86400")
                .build();
    }

    @GET
    @Path("/background/{userId}")
    @PermitAll
    @Produces("image/jpeg")
    public Response getBackground(@PathParam("userId") Long userId) {
        log.info("UserResource/getBackground: " + userId);
        String base = backgroundUploadDir.replace("${user.home}", System.getProperty("user.home"));
        File file = Paths.get(base, userId + "_background.jpg").toFile();
        if (!file.exists() || !file.isFile()) {
            return Response.status(404).entity("Hintergrund nicht gefunden").build();
        }
        return Response.ok(file)
                .header("Cache-Control", "public, max-age=86400")
                .build();
    }

    @GET
    @Path("/members")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTeamMembers() {
        log.info("UserResource/getTeamMembers");
        return Response.ok(userOrm.getTeamMembers()).build();
    }

    private BufferedImage scaleImage(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim)
            return src;
        double ratio = (double) maxDim / Math.max(w, h);
        int newW = (int) Math.round(w * ratio);
        int newH = (int) Math.round(h * ratio);
        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return scaled;
    }

    public static class PhotoUploadForm {
        @org.jboss.resteasy.reactive.RestForm("file")
        public org.jboss.resteasy.reactive.multipart.FileUpload file;
    }
}
