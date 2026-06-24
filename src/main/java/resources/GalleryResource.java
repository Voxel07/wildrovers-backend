package resources;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;

import model.Gallery;
import model.User;
import orm.GalleryOrm;
import tools.HtmlSanitizer;

import java.util.List;
import java.util.logging.Logger;

@Path("/gallery")
@RequestScoped
public class GalleryResource {

    private static final Logger log = Logger.getLogger(GalleryResource.class.getName());

    @Inject
    GalleryOrm galleryOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    HtmlSanitizer htmlSanitizer;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllGalleries() {
        log.info("GalleryResource/getAllGalleries");
        List<Gallery> list = galleryOrm.getAllGalleries();
        return Response.ok(list).build();
    }

    @POST
    @RolesAllowed({ "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addGallery(@Valid Gallery gallery) {
        log.info("GalleryResource/addGallery");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        // Sanitize user inputs
        gallery.setTitle(htmlSanitizer.sanitizeTitle(gallery.getTitle()));
        gallery.setLocation(htmlSanitizer.sanitizeTitle(gallery.getLocation()));
        gallery.setUrl(htmlSanitizer.sanitizeTitle(gallery.getUrl()));

        // Basic validation
        if (gallery.getTitle().isBlank() || gallery.getUrl().isBlank() || gallery.getLocation().isBlank() || gallery.getDate() == null) {
            return Response.status(400).entity("Alle Felder müssen ausgefüllt sein.").build();
        }

        try {
            Gallery created = galleryOrm.addGallery(gallery, user.getId());
            return Response.status(201).entity(created).build();
        } catch (Exception e) {
            return Response.status(500).entity("Fehler beim Speichern der Galerie").build();
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({ "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateGallery(@PathParam("id") Long id, @Valid Gallery gallery) {
        log.info("GalleryResource/updateGallery: " + id);
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        Gallery existing = galleryOrm.getGalleryById(id);
        if (existing == null) {
            return Response.status(404).entity("Galerie nicht gefunden").build();
        }

        // Authorization check: creator or Admin
        if (!existing.getCreator().getId().equals(user.getId()) && !user.getRole().equals("Admin")) {
            return Response.status(403).entity("Nur der Ersteller oder ein Administrator darf dieses Album bearbeiten.")
                    .build();
        }

        // Sanitize and copy fields
        existing.setTitle(htmlSanitizer.sanitizeTitle(gallery.getTitle()));
        existing.setLocation(htmlSanitizer.sanitizeTitle(gallery.getLocation()));
        existing.setUrl(htmlSanitizer.sanitizeTitle(gallery.getUrl()));
        existing.setDate(gallery.getDate());

        try {
            Gallery updated = galleryOrm.updateGallery(existing);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(500).entity("Fehler beim Aktualisieren der Galerie").build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({ "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteGallery(@PathParam("id") Long id) {
        log.info("GalleryResource/deleteGallery: " + id);
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        Gallery existing = galleryOrm.getGalleryById(id);
        if (existing == null) {
            return Response.status(404).entity("Galerie nicht gefunden").build();
        }

        // Authorization check: creator or Admin
        if (!existing.getCreator().getId().equals(user.getId()) && !user.getRole().equals("Admin")) {
            return Response.status(403).entity("Nur der Ersteller oder ein Administrator darf dieses Album löschen.")
                    .build();
        }

        try {
            galleryOrm.deleteGallery(id);
            return Response.ok().entity("Galerie gelöscht").build();
        } catch (Exception e) {
            return Response.status(500).entity("Fehler beim Löschen der Galerie").build();
        }
    }
}
