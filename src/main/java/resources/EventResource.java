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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;

import model.Event;
import model.User;
import orm.EventOrm;
import orm.UserOrm;
import tools.HtmlSanitizer;
import model.Forum.ForumPost;
import model.EventAttendance;
import tools.AuditLogger;
import tools.NotificationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import tools.Telemetry;

@Path("/event")
@RequestScoped
public class EventResource {

    private static final DateTimeFormatter EVENT_FORUM_TITLE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'");

    @Inject
    EventOrm eventOrm;

    @Inject
    UserOrm userOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    HtmlSanitizer htmlSanitizer;

    @Inject
    NotificationService notificationService;

    @Inject
    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @Inject
    tools.EventCalendarSync calendarSync;

    /** Forum topic that receives the discussion post of each new event. */
    @Inject
    @ConfigProperty(name = "app.events.forum-topic-id", defaultValue = "1")
    Long eventTopicId;

    /**
     * Without parameters all events are returned. {@code year} or an ISO date range
     * ({@code from} inclusive, {@code to} exclusive) limit the list.
     */
    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllEvents(@QueryParam("year") Integer year,
                                 @QueryParam("from") String from,
                                 @QueryParam("to") String to) {
        List<Event> list;
        try {
            if (year != null) {
                if (year < 1900 || year > 9999) return Response.status(400).entity("Ungültiges Jahr").build();
                LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
                list = eventOrm.getEventsBetween(start, start.plusYears(1));
            } else if (from != null || to != null) {
                LocalDateTime start = from != null ? LocalDate.parse(from).atStartOfDay() : LocalDateTime.of(1900, 1, 1, 0, 0);
                LocalDateTime end = to != null ? LocalDate.parse(to).atStartOfDay() : LocalDateTime.of(9999, 1, 1, 0, 0);
                list = eventOrm.getEventsBetween(start, end);
            } else {
                list = eventOrm.getAllEvents();
            }
        } catch (java.time.format.DateTimeParseException e) {
            return Response.status(400).entity("Datum muss im Format JJJJ-MM-TT angegeben werden").build();
        }
        return Response.ok(forReader(list)).build();
    }

    @GET
    @Path("/years")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public List<Integer> getEventYears() {
        return eventOrm.getEventYears();
    }

    @GET
    @Path("/upcoming")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUpcomingEvents() {
        List<Event> list = eventOrm.getUpcomingEvents(3);
        return Response.ok(forReader(list)).build();
    }

    /**
     * Not transactional on purpose: the event and its forum post are written in one database
     * transaction, the Google Calendar call happens after the commit (see EventCalendarSync).
     */
    @POST
    @RolesAllowed({ "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addEvent(@Valid Event input) {
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        AuditLogger.crud(user.getUserName(), user.getId(), "CREATE", "Event",
                input.getTitle());

        // Only user-editable fields; ids, links and sync state are never taken from the client.
        Event event = new Event();
        String error = applyUserFields(event, input);
        if (error != null) {
            return Response.status(400).entity(error).build();
        }

        ForumPost post = new ForumPost();
        post.setTitle(formatEventTitleForPost(event));
        post.setContent(formatEventPostContent(event));

        Event created;
        try {
            created = eventOrm.addEvent(event, user.getId(), post, eventTopicId, frontendUrl);
        } catch (EventOrm.ForumPostFailedException e) {
            Telemetry.warn("Event not created", e, "event.title", event.getTitle(), "forum.topic.id", eventTopicId, "http.response.status_code", e.status);
            boolean topicMissing = e.status == 404;
            String message = topicMissing
                    ? "Das Forum-Thema für Events (app.events.forum-topic-id=" + eventTopicId + ") existiert nicht."
                    : e.getMessage();
            return Response.status(topicMissing ? 500 : e.status).entity(message).build();
        } catch (Exception e) {
            Telemetry.error("Creating event failed", e);
            return Response.status(500).entity("Fehler beim Erstellen des Events").build();
        }

        calendarSync.sync(created.getId());
        Event stored = eventOrm.getEventById(created.getId());
        notificationService.recordEvent(stored, "CREATED", user.getId(), frontendUrl);
        return Response.status(201).entity(forReader(stored)).build();
    }

    /**
     * Copies the sanitised, user-editable fields and validates them.
     *
     * @return an error message for the client, or null if the input is valid
     */
    private String applyUserFields(Event target, Event input) {
        target.setTitle(htmlSanitizer.sanitizeTitle(input.getTitle()));
        target.setLocation(htmlSanitizer.sanitizeTitle(input.getLocation()));
        target.setEventDate(input.getEventDate());
        target.setEventEndDate(input.getEventEndDate());
        target.setDescription(input.getDescription() != null ? htmlSanitizer.sanitize(input.getDescription()) : null);

        if (target.getTitle() == null || target.getTitle().isBlank() || target.getLocation() == null
                || target.getLocation().isBlank() || target.getEventDate() == null) {
            return "Alle Felder müssen ausgefüllt sein.";
        }
        if (target.getEventEndDate() != null && target.getEventEndDate().isBefore(target.getEventDate())) {
            return "Das Ende darf nicht vor dem Beginn liegen.";
        }
        // Sanitising escapes characters, so the stored text can be longer than the input.
        if (target.getDescription() != null && target.getDescription().length() > Event.DESCRIPTION_MAX_LENGTH) {
            return "Die Beschreibung ist zu lang (max. " + Event.DESCRIPTION_MAX_LENGTH
                    + " Zeichen inklusive Formatierung).";
        }
        return null;
    }

    static String formatEventTitleForPost(Event event) {
        String title = "Event: " + event.getTitle();
        if (event.getEventDate() == null) {
            return title;
        }
        return title + " (" + event.getEventDate().format(EVENT_FORUM_TITLE_DATE_FORMAT) + ")";
    }

    private String formatEventPostContent(Event event) {
        return "<p><strong>Termin:</strong> " + formatEventDateForPost(event.getEventDate(), event.getEventEndDate()) + "</p>"
                + "<p><strong>Ort:</strong> " + event.getLocation() + "</p>"
                + "<hr/>"
                + "<p>" + (event.getDescription() != null ? event.getDescription() : "") + "</p>";
    }

    private String formatEventDateForPost(java.time.LocalDateTime dateTime, java.time.LocalDateTime endDateTime) {
        if (dateTime == null)
            return "";
        String startStr = dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'"));
        if (endDateTime != null) {
            if (dateTime.toLocalDate().equals(endDateTime.toLocalDate())) {
                return startStr + " bis " + endDateTime.format(DateTimeFormatter.ofPattern("HH:mm 'Uhr'"));
            } else {
                return startStr + " bis " + endDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'"));
            }
        }
        return startStr;
    }

    /** Not transactional: see {@link #addEvent}. The forum post is updated with the event. */
    @PUT
    @RolesAllowed({ "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateEvent(@Valid Event event) {
        Telemetry.attr("event.id", event.getId());
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        AuditLogger.crud(user.getUserName(), user.getId(), "UPDATE", "Event",
                event.getId());

        Event existing = event.getId() != null ? eventOrm.getEventById(event.getId()) : null;
        if (existing == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        // Authorization check: creator or moderator
        boolean isCreator = existing.getCreator() != null && existing.getCreator().getId().equals(user.getId());
        if (!isCreator && !model.Users.Roles.isModerator(user.getRole())) {
            return Response.status(403).entity("Nur der Ersteller oder ein Moderator darf dieses Event bearbeiten.")
                    .build();
        }

        Event changes = new Event();
        String error = applyUserFields(changes, event);
        if (error != null) {
            return Response.status(400).entity(error).build();
        }

        Event updated;
        try {
            updated = eventOrm.updateEvent(existing.getId(), changes, formatEventTitleForPost(changes),
                    formatEventPostContent(changes), user.getId());
        } catch (Exception e) {
            Telemetry.error("Updating event failed", e, "event.id", event.getId());
            return Response.status(500).entity("Fehler beim Aktualisieren des Events").build();
        }
        if (updated == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        calendarSync.sync(updated.getId());
        notificationService.recordEvent(updated, "UPDATED", user.getId(), frontendUrl);
        return Response.ok(forReader(eventOrm.getEventById(updated.getId()))).build();
    }

    /** Deletes the event with its forum post; the calendar entry is removed after the commit. */
    @DELETE
    @Path("/{id}")
    @RolesAllowed({ "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteEvent(@PathParam("id") Long id) {
        Telemetry.attr("event.id", id);
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        AuditLogger.crud(user.getUserName(), user.getId(), "DELETE", "Event", id);

        Event existing = eventOrm.getEventById(id);
        if (existing == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        // Authorization check: creator or moderator
        boolean isCreator = existing.getCreator() != null && existing.getCreator().getId().equals(user.getId());
        if (!isCreator && !model.Users.Roles.isModerator(user.getRole())) {
            return Response.status(403).entity("Nur der Ersteller oder ein Moderator darf dieses Event löschen.")
                    .build();
        }

        String googleCalendarEventId;
        try {
            googleCalendarEventId = eventOrm.deleteEvent(id);
        } catch (Exception e) {
            Telemetry.error("Deleting event failed", e, "event.id", id);
            return Response.status(500).entity("Fehler beim Löschen des Events").build();
        }
        calendarSync.delete(googleCalendarEventId);
        notificationService.recordEvent(existing, "DELETED", user.getId(), frontendUrl);
        return Response.ok().entity("Event gelöscht").build();
    }

    /** Visitors may RSVP as guests; only team members appear in the non-respondent list. */
    @POST
    @Path("/{id}/attendance")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAttendance(@PathParam("id") Long eventId, EventAttendance attendanceInput) {
        Telemetry.attr("event.id", eventId);
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }
        if (attendanceInput == null || attendanceInput.getStatus() == null) {
            return Response.status(400).entity("Ungültiger Status").build();
        }
        AuditLogger.crud(user.getUserName(), user.getId(), "ATTEND", "Event",
                eventId, "status=" + attendanceInput.getStatus());

        Event event = eventOrm.getEventById(eventId);
        if (event == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        String status = attendanceInput.getStatus().toUpperCase();
        if (!"YES".equals(status) && !"NO".equals(status) && !"MAYBE".equals(status)) {
            return Response.status(400).entity("Ungültiger Status").build();
        }

        // Delegates to EventOrm which handles DB persistence AND cache invalidation
        Event updatedEvent = eventOrm.saveAttendance(eventId, user.getId(), status);
        if (updatedEvent == null) {
            return Response.status(500).entity("Fehler beim Speichern der Teilnahme").build();
        }

        notificationService.acknowledgeEvent(user.getId(), eventId);

        return Response.ok(forReader(updatedEvent)).build();
    }

    @POST
    @Path("/{id}/attendance/{userId}")
    @RolesAllowed({ "Vorstand", "Admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAttendanceForUser(@PathParam("id") Long eventId, @PathParam("userId") Long userId,
            EventAttendance attendanceInput) {
        Telemetry.attrs("event.id", eventId, "event.attendance.user.id", userId);
        User actor = userPrincipalResolver.resolveUser();
        if (actor == null) {
            return Response.status(401).entity("Nicht eingeloggt").build();
        }

        User target = userOrm.findById(userId);
        if (target == null) {
            return Response.status(404).entity("Benutzer nicht gefunden").build();
        }
        if (!model.Users.Roles.hasRequiredRole(target.getRole(), model.Users.Roles.FRESHMAN)) {
            return Response.status(400).entity("Nur Teammitglieder können als Teilnehmer eingetragen werden.").build();
        }

        String status = attendanceInput == null || attendanceInput.getStatus() == null
                ? null : attendanceInput.getStatus().toUpperCase(java.util.Locale.ROOT);
        if (!"YES".equals(status) && !"NO".equals(status) && !"MAYBE".equals(status)) {
            return Response.status(400).entity("Ungültiger Status").build();
        }

        AuditLogger.crud(actor.getUserName(), actor.getId(), "ATTEND", "Event", eventId,
                "status=" + status + " user=" + target.getUserName());

        Event updatedEvent = eventOrm.saveAttendance(eventId, userId, status);
        if (updatedEvent == null) {
            return Response.status(404).entity("Event nicht gefunden").build();
        }

        return Response.ok(forReader(updatedEvent)).build();
    }

    @GET
    @Path("/by-post/{postId}")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEventByPost(@PathParam("postId") Long postId) {
        Telemetry.attr("forum.post.id", postId);
        Event event = eventOrm.getEventByForumPostId(postId);
        if (event == null) {
            // Most forum posts are not event announcements, so this is optional.
            return Response.noContent().build();
        }
        return Response.ok(forReader(event)).build();
    }

    /**
     * Returns per-request copies of the given events with the non-respondent list for the current
     * reader. Events come from shared caches, so they must never be modified here.
     */
    private List<Event> forReader(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return events;
        }
        User currentUser = userPrincipalResolver.resolveUser();
        boolean isTeam = currentUser != null && model.Users.Roles.hasRequiredRole(currentUser.getRole(), model.Users.Roles.FRESHMAN);
        List<User> team = isTeam ? userOrm.getTeamMembers() : null;

        List<Event> result = new java.util.ArrayList<>(events.size());
        for (Event e : events) {
            List<String> nonResps = new java.util.ArrayList<>();
            if (team != null) {
                java.util.Set<String> respondedUsernames = new java.util.HashSet<>();
                if (e.getAttendances() != null) {
                    for (EventAttendance att : e.getAttendances()) {
                        if (att.getUser() != null) {
                            respondedUsernames.add(att.getUser().getUserName());
                        }
                    }
                }
                for (User u : team) {
                    if (!respondedUsernames.contains(u.getUserName())) {
                        nonResps.add(u.getUserName());
                    }
                }
            }
            result.add(e.copyWithNonRespondents(nonResps));
        }
        return result;
    }

    private Event forReader(Event event) {
        return event == null ? null : forReader(java.util.Collections.singletonList(event)).get(0);
    }
}
