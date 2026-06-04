package resources;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;

import model.Event;
import model.User;
import orm.EventOrm;
import tools.HtmlSanitizer;
import tools.GoogleCalendarService;
import model.Forum.ForumPost;
import orm.Forum.ForumPostOrm;
import model.EventAttendance;
import jakarta.persistence.EntityManager;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import java.util.List;
import java.util.logging.Logger;

@Path("/event")
@RequestScoped
public class EventResource {

    private static final Logger log = Logger.getLogger(EventResource.class.getName());

    @Inject
    EventOrm eventOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    HtmlSanitizer htmlSanitizer;

    @Inject
    GoogleCalendarService googleCalendarService;

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    EntityManager em;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllEvents() {
        log.info("EventResource/getAllEvents");
        List<Event> list = eventOrm.getAllEvents();
        return Response.ok(list).build();
    }

    @GET
    @Path("/upcoming")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUpcomingEvents() {
        log.info("EventResource/getUpcomingEvents");
        List<Event> list = eventOrm.getUpcomingEvents(3);
        return Response.ok(list).build();
    }

    @POST
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addEvent(@Valid Event event) {
        log.info("EventResource/addEvent");
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        // Sanitize inputs
        event.setTitle(htmlSanitizer.sanitizeTitle(event.getTitle()));
        event.setLocation(htmlSanitizer.sanitizeTitle(event.getLocation()));
        if (event.getDescription() != null) {
            event.setDescription(htmlSanitizer.sanitize(event.getDescription()));
        }

        if (event.getTitle().isBlank() || event.getLocation().isBlank() || event.getEventDate() == null) {
            return Response.status(400).entity("Alle Felder müssen ausgefüllt sein.").build();
        }

        // Create a forum post under Topic ID 2
        Long postId = null;
        try {
            ForumPost post = new ForumPost();
            post.setTitle("Event: " + event.getTitle());
            String postContent = "<p><strong>Termin:</strong> " + formatEventDateForPost(event.getEventDate()) + "</p>"
                    + "<p><strong>Ort:</strong> " + event.getLocation() + "</p>"
                    + "<hr/>"
                    + "<p>" + (event.getDescription() != null ? event.getDescription() : "") + "</p>";
            post.setContent(postContent);
            Response postResponse = forumPostOrm.addPost(post, 1L, user.getId());
            if (postResponse.getStatus() == 201) {
                postId = (Long) postResponse.getEntity();
                String forumUrl = "http://localhost:5173/Forum/Post/" + postId;
                event.setForumPostUrl(forumUrl);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to create forum post for event", e);
        }

        // 1. Sync to Google Calendar
        String googleEventId = googleCalendarService.syncEvent(event);
        if (googleEventId != null) {
            event.setGoogleCalendarEventId(googleEventId);
        }

        // 2. Save in Database
        try {
            Event created = eventOrm.addEvent(event, user.getId());
            return Response.status(201).entity(created).build();
        } catch (Exception e) {
            // Rollback calendar entry if DB save fails
            if (googleEventId != null) {
                googleCalendarService.deleteEvent(googleEventId);
            }
            return Response.status(500).entity("Fehler beim Erstellen des Events").build();
        }
    }

    private String formatEventDateForPost(java.time.LocalDateTime dateTime) {
        if (dateTime == null)
            return "";
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'"));
    }

    @PUT
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateEvent(@Valid Event event) {
        log.info("EventResource/updateEvent: " + event.getId());
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        Event existing = eventOrm.getEventById(event.getId());
        if (existing == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        // Authorization check: creator or Admin
        if (!existing.getCreator().getId().equals(user.getId()) && !user.getRole().equals("Admin")) {
            return Response.status(403).entity("Nur der Ersteller oder ein Administrator darf dieses Event bearbeiten.")
                    .build();
        }

        // Sanitize and copy fields
        existing.setTitle(htmlSanitizer.sanitizeTitle(event.getTitle()));
        existing.setLocation(htmlSanitizer.sanitizeTitle(event.getLocation()));
        existing.setEventDate(event.getEventDate());
        if (event.getDescription() != null) {
            existing.setDescription(htmlSanitizer.sanitize(event.getDescription()));
        } else {
            existing.setDescription(null);
        }

        // 1. Sync to Google Calendar
        String googleEventId = googleCalendarService.syncEvent(existing);
        if (googleEventId != null) {
            existing.setGoogleCalendarEventId(googleEventId);
        }

        // 2. Update Database
        try {
            Event updated = eventOrm.updateEvent(existing);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return Response.status(500).entity("Fehler beim Aktualisieren des Events").build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteEvent(@PathParam("id") Long id) {
        log.info("EventResource/deleteEvent: " + id);
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        Event existing = eventOrm.getEventById(id);
        if (existing == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        // Authorization check: creator or Admin
        if (!existing.getCreator().getId().equals(user.getId()) && !user.getRole().equals("Admin")) {
            return Response.status(403).entity("Nur der Ersteller oder ein Administrator darf dieses Event löschen.")
                    .build();
        }

        // 1. Delete from Google Calendar
        if (existing.getGoogleCalendarEventId() != null) {
            googleCalendarService.deleteEvent(existing.getGoogleCalendarEventId());
        }

        // 2. Delete from Database
        try {
            eventOrm.deleteEvent(id);
            return Response.ok().entity("Event gelöscht").build();
        } catch (Exception e) {
            return Response.status(500).entity("Fehler beim Löschen des Events").build();
        }
    }

    @POST
    @Path("/{id}/attendance")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @jakarta.transaction.Transactional
    public Response setAttendance(@PathParam("id") Long eventId, EventAttendance attendanceInput) {
        log.info("EventResource/setAttendance: " + eventId);
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        Event event = eventOrm.getEventById(eventId);
        if (event == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        String status = attendanceInput.getStatus().toUpperCase();
        if (!"YES".equals(status) && !"NO".equals(status) && !"MAYBE".equals(status)) {
            return Response.status(400).entity("Ungültiger Status").build();
        }

        jakarta.persistence.TypedQuery<EventAttendance> query = em.createQuery(
                "SELECT a FROM EventAttendance a WHERE a.user.id = :userId AND a.event.id = :eventId",
                EventAttendance.class);
        query.setParameter("userId", user.getId());
        query.setParameter("eventId", eventId);

        List<EventAttendance> list = query.getResultList();
        if (list.isEmpty()) {
            EventAttendance attendance = new EventAttendance();
            attendance.setUser(user);
            attendance.setEvent(event);
            attendance.setStatus(status);
            em.persist(attendance);
        } else {
            EventAttendance attendance = list.get(0);
            attendance.setStatus(status);
            em.merge(attendance);
        }

        return Response.ok(eventOrm.getEventById(eventId)).build();
    }
}
