package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

//Logging zeug

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
import model.Users.Roles;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;

//Sicherheits Zeug
// Validator
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import tools.Telemetry;

@Path("/user")
@RequestScoped
public class UserResouce {

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

    /**
     * The current account. Activity counters cost five queries, so they are only
     * computed with {@code ?stats=true} (the profile page).
     */
    @GET
    @Path("/me")
    @io.quarkus.security.Authenticated // disabled accounts get no roles; resolveUser() explains the 403
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMe(@QueryParam("stats") @DefaultValue("false") boolean stats) {
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Benutzer nicht eingeloggt oder unbekannt").build();
        }
        if (!stats) {
            return Response.ok(user).build();
        }
        user.setEventsAttended(userOrm.getEventsAttendedCount(user.getId()));
        user.setForumPostCount(userOrm.getForumPostCount(user.getId()));
        user.setForumAnswerCount(userOrm.getForumAnswerCount(user.getId()));
        user.setForumTopicCount(userOrm.getForumTopicCount(user.getId()));
        user.setForumCategoryCount(userOrm.getForumCategoryCount(user.getId()));
        return Response.ok(user).build();
    }

    @GET
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<User> getUser(@QueryParam("userId") Long userId, @QueryParam("username") String userName) {
        if (userId != null) {
            Telemetry.attrs("user.query", "id", "user.id", userId);
            return userOrm.getUserById(userId);
        } else if (userName != null) {
            Telemetry.attrs("user.query", "username", "user.name", userName);
            return userOrm.getUserByUsername(userName);
        } else {
            Telemetry.attr("user.query", "all");
            return userOrm.getUsers();
        }
    }

    @POST
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUser(User user) {
        User admin = userPrincipalResolver.resolveUser();
        if (user == null || user.getId() == null) {
            return Response.status(400).entity("Benutzer-ID fehlt.").build();
        }
        AuditLogger.crud(admin != null ? admin.getUserName() : "unknown",
                admin != null ? admin.getId() : null,
                "UPDATE", "User", user.getId(), "userName=" + user.getUserName());
        if (admin != null && Roles.ALDERMEN.equals(admin.getRole())) {
            List<User> targets = userOrm.getUserById(user.getId());
            if (targets.isEmpty()) {
                return Response.status(404).entity("Benutzer nicht gefunden.").build();
            }
            if (Roles.ADMIN.equals(targets.get(0).getRole()) || Roles.ADMIN.equals(user.getRole())) {
                Telemetry.warn("Vorstand attempted a forbidden Admin account change", "user.id", user.getId(), "security.event", "forbidden_admin_change");
                return Response.status(403).entity("Vorstand darf Admin-Benutzer weder bearbeiten noch ernennen.").build();
            }
        }
        return userOrm.updateUser(user);
    }

    @POST
    @Path("/resend-verification")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resendVerification(User credentials) {
        return userOrm.resendVerification(credentials);
    }

    @PUT
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addUser(@Valid model.Users.SignupRequest signup) {
        if (!signupSettings.isSignupEnabled()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Die Registrierung ist derzeit deaktiviert.")
                    .build();
        }
        if (signup == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Registrierungsdaten fehlen.").build();
        }
        AuditLogger.crud(signup.userName, null, "REGISTER", "User", signup.userName);
        return userOrm.addUser(signup);
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
        Telemetry.attr("user.id", userId);
        try {
            String result = forumPostOrm.deleteAllPostsFromUser(userId);
            return Response.ok(result).build();
        } catch (Exception e) {
            Telemetry.error("Deleting posts of user failed", e, "user.id", userId);
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
        Telemetry.attr("signup.enabled", enabled);
        User admin = userPrincipalResolver.resolveUser();
        AuditLogger.crud(admin != null ? admin.getUserName() : "unknown",
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
        Telemetry.attrs("user.id", userId, "user.delete.account", deleteAccount, "user.delete.events", deleteEvents,
                "user.delete.posts", deletePosts, "user.delete.gallery", deleteGallery, "user.delete.hard", hardDelete);
        User admin = userPrincipalResolver.resolveUser();
        AuditLogger.crud(admin != null ? admin.getUserName() : "unknown",
                admin != null ? admin.getId() : null,
                "DELETE", "User", userId,
                "hardDelete=" + hardDelete + " events=" + deleteEvents + " posts=" + deletePosts + " gallery=" + deleteGallery);
        // Block Vorstand from deleting Admin users
        if (admin != null && "Vorstand".equals(admin.getRole())) {
            List<User> targetUsers = userOrm.getUserById(userId);
            if (!targetUsers.isEmpty() && "Admin".equals(targetUsers.get(0).getRole())) {
                return Response.status(403).entity("Vorstand darf keine Admin-Benutzer löschen.").build();
            }
        }
        return userOrm.deleteUserWithOptions(userId, deleteAccount, deleteEvents, deletePosts, deleteGallery, hardDelete);
    }

    @POST
    @Path("/me/profile")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateProfile(User profileData) {
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Benutzer nicht eingeloggt").build();
        }
        AuditLogger.crud(user.getUserName(), user.getId(), "UPDATE", "Profile",
                user.getId());
        String phrase = null;
        if (profileData.getPhrase() != null) {
            phrase = htmlSanitizer.sanitizeTitle(profileData.getPhrase());
        }
        try {
            userOrm.updateUserProfile(user.getId(), phrase, profileData.getBirthday(), profileData.getFirstName(),
                    profileData.getLastName(), profileData.getEmail(), profileData.getUserName());
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
            BufferedImage original = tools.SafeImageReader.readOriented(uploadedFile, 25_000_000L);
            if (original == null) {
                return Response.status(400).entity("Ungültiges Bildformat").build();
            }

            BufferedImage scaled = scaleImage(original, 400);

            String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
            java.nio.file.Path dir = Paths.get(base);
            Files.createDirectories(dir);
            java.nio.file.Path destPath = dir.resolve(user.getId() + ".jpg");

            // Converts alpha to a white background and fails loudly if nothing was written.
            tools.SafeImageReader.write(scaled, "jpg", destPath);

            String photoUrl = "/user/photo/" + user.getId() + "?v=" + System.currentTimeMillis();
            userOrm.updateUserPhotoUrl(user.getId(), photoUrl);

            jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                    .add("photoUrl", photoUrl)
                    .build();
            return Response.ok(result).build();
        } catch (Exception e) {
            Telemetry.error("Failed to upload photo", e);
            return Response.status(500).entity("Fehler beim Verarbeiten des Fotos").build();
        }
    }

    @POST
    @Path("/me/background")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadBackground(@jakarta.ws.rs.BeanParam PhotoUploadForm form) {
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
            BufferedImage original = tools.SafeImageReader.readOriented(uploadedFile, 25_000_000L);
            if (original == null) {
                return Response.status(400).entity("Ungültiges Bildformat").build();
            }

            BufferedImage scaled = scaleImage(original, 1200);

            String base = backgroundUploadDir.replace("${user.home}", System.getProperty("user.home"));
            java.nio.file.Path dir = Paths.get(base);
            Files.createDirectories(dir);
            java.nio.file.Path destPath = dir.resolve(user.getId() + "_background.jpg");

            // Converts alpha to a white background and fails loudly if nothing was written.
            tools.SafeImageReader.write(scaled, "jpg", destPath);

            String backgroundUrl = "/user/background/" + user.getId() + "?v=" + System.currentTimeMillis();
            userOrm.updateUserBackgroundUrl(user.getId(), backgroundUrl);

            jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                    .add("backgroundUrl", backgroundUrl)
                    .build();
            return Response.ok(result).build();
        } catch (Exception e) {
            Telemetry.error("Failed to upload background", e);
            return Response.status(500).entity("Fehler beim Verarbeiten des Hintergrundbildes").build();
        }
    }

    @DELETE
    @Path("/me/background")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBackground() {
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
            Telemetry.error("Failed to delete background", e);
            return Response.status(500).entity("Fehler beim Löschen des Hintergrundbildes").build();
        }
    }

    @DELETE
    @Path("/me/photo")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePhoto() {
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        try {
            String base = uploadDir.replace("${user.home}", System.getProperty("user.home"));
            java.nio.file.Path filePath = Paths.get(base, user.getId() + ".jpg");
            File file = filePath.toFile();
            if (file.exists() && file.isFile()) {
                Files.deleteIfExists(filePath);
            }
            userOrm.updateUserPhotoUrl(user.getId(), null);

            return Response.ok(jakarta.json.Json.createObjectBuilder()
                    .add("message", "Profilbild gelöscht")
                    .build()).build();
        } catch (Exception e) {
            Telemetry.error("Failed to delete photo", e);
            return Response.status(500).entity("Fehler beim Löschen des Profilbildes").build();
        }
    }

    @GET
    @Path("/photo/{userId}")
    @PermitAll
    @Produces("image/jpeg")
    public Response getPhoto(@PathParam("userId") Long userId) {
        Telemetry.attr("user.id", userId);
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
        Telemetry.attr("user.id", userId);
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
        List<User> members = userOrm.getTeamMembers();
        User currentUser = userPrincipalResolver.resolveUser();
        boolean isTeamMember = currentUser != null
                && model.Users.Roles.hasRequiredRole(currentUser.getRole(), model.Users.Roles.FRESHMAN);
        if (!isTeamMember) {
            return Response.ok(members.stream().map(PublicMember::of).toList()).build();
        }
        return Response.ok(members.stream().map(TeamMember::of).toList()).build();
    }

    /** Explicit public projection: adding a field to User can never leak it here. */
    public record PublicMember(Long id, String userName, String role, String photoUrl,
            String backgroundUrl, String phrase, String ribbon) {
        static PublicMember of(User u) {
            return new PublicMember(u.getId(), u.getUserName(), u.getRole(),
                    u.getPhotoUrl(), u.getBackgroundUrl(), u.getPhrase(), u.getRibbon());
        }
    }

    /**
     * What team members see of each other. Contact details, login metadata
     * (IP, country, times), block and fee status stay with the board.
     */
    public record TeamMember(Long id, String userName, String role, String firstName, String lastName,
            String photoUrl, String backgroundUrl, String phrase, String ribbon, java.time.LocalDate birthday,
            Long regDate, Integer visitedOps) {
        static TeamMember of(User u) {
            return new TeamMember(u.getId(), u.getUserName(), u.getRole(), u.getFirstName(), u.getLastName(),
                    u.getPhotoUrl(), u.getBackgroundUrl(), u.getPhrase(), u.getRibbon(), u.getBirthday(),
                    u.getRegDate(), u.getVisitedOps());
        }
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
        // JPEG has no alpha: paint transparent areas white instead of black.
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, newW, newH);
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
