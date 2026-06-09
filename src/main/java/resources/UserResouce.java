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
import jakarta.ws.rs.QueryParam;

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
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    tools.HtmlSanitizer htmlSanitizer;

    @ConfigProperty(name = "user.photos.upload-dir", defaultValue = "${user.home}/wildrovers-uploads/user-photos")
    String uploadDir;



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
        return userOrm.addUser(usr);
    }

    @DELETE
    @Path("/{userId}")
    @RolesAllowed({ "Admin", "Vorstand" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(@jakarta.ws.rs.PathParam("userId") Long userId) {
        log.info("UserResource/deleteUser: " + userId);
        return userOrm.deleteUser(userId);
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
