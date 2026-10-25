package orm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.time.ZoneId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.LocalDateTime;
import java.util.List;
import model.Event;
import model.User;
import model.Forum.ForumPost;
import orm.Forum.ForumPostOrm;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidateAll;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;

@ApplicationScoped
public class EventOrm {


    @Inject
    EntityManager em;

    @Inject
    ForumPostOrm forumPostOrm;

    /** Event dates are local wall-clock times of the team; the server may run in UTC. */
    @ConfigProperty(name = "notification.time-zone", defaultValue = "Europe/Berlin")
    ZoneId timeZone;

    /** Thrown inside a transaction to roll back an event whose forum post could not be written. */
    public static class ForumPostFailedException extends RuntimeException {
        public final int status;

        public ForumPostFailedException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /** Loads creator, attendances and their users in the same query (no select per event). */
    private static final String WITH_ATTENDANCES =
            "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.creator LEFT JOIN FETCH e.attendances a LEFT JOIN FETCH a.user";

    @WithSpan
    public List<Event> getAllEvents() {
        TypedQuery<Event> query = em.createQuery(WITH_ATTENDANCES + " ORDER BY e.eventDate ASC", Event.class);
        return query.getResultList();
    }

    /** Events starting in [from, to). */
    @WithSpan
    public List<Event> getEventsBetween(LocalDateTime from, LocalDateTime to) {
        Telemetry.attrs("event.range.from", from, "event.range.to", to);
        return em.createQuery(WITH_ATTENDANCES
                        + " WHERE e.eventDate >= :from AND e.eventDate < :to ORDER BY e.eventDate ASC", Event.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Years that have at least one event, ascending. */
    @CacheResult(cacheName = "event-years")
    @WithSpan
    public List<Integer> getEventYears() {
        return em.createQuery("SELECT DISTINCT EXTRACT(YEAR FROM e.eventDate) FROM Event e WHERE e.eventDate IS NOT NULL",
                        Integer.class)
                .getResultList()
                .stream()
                .sorted()
                .toList();
    }

    @WithSpan
    public List<Event> getUpcomingEvents(int limit) {
        Telemetry.attr("event.limit", limit);
        LocalDateTime now = LocalDateTime.now(timeZone);
        // Limit on ids first: a row limit on a collection fetch would page in memory.
        List<Long> ids = em.createQuery("SELECT e.id FROM Event e WHERE e.eventDate >= :now ORDER BY e.eventDate ASC", Long.class)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
        if (ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return em.createQuery(WITH_ATTENDANCES + " WHERE e.id IN :ids ORDER BY e.eventDate ASC", Event.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @WithSpan
    public Event getEventById(Long id) {
        Telemetry.attr("event.id", id);
        TypedQuery<Event> query = em.createQuery(WITH_ATTENDANCES + " WHERE e.id = :id", Event.class);
        query.setParameter("id", id);
        List<Event> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /** Loads an event bypassing the caches, in its own short transaction so the data is fresh. */
    @Transactional
    public Event findUncached(Long id) {
        return id == null ? null : em.find(Event.class, id);
    }

    /**
     * Creates the discussion post and the event in one transaction: if the post cannot be
     * written (missing topic, no permission, duplicate title) nothing is saved.
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "events-in-range"),
        @CacheInvalidateAll(cacheName = "event-years"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @WithSpan
    public Event addEvent(Event event, Long userId, ForumPost post, Long topicId, String frontendUrl) {
        Telemetry.attrs("user.id", userId, "forum.topic.id", topicId);
        User user = em.find(User.class, userId);
        if (user == null) {
            Telemetry.warn("User not found", "user.id", userId);
            throw new IllegalArgumentException("Benutzer nicht gefunden.");
        }
        Response postResponse = forumPostOrm.addPost(post, topicId, userId);
        if (postResponse.getStatus() != 201 || !(postResponse.getEntity() instanceof Long postId)) {
            throw new ForumPostFailedException(postResponse.getStatus(),
                    "Der Forenbeitrag zum Event konnte nicht erstellt werden: " + postResponse.getEntity());
        }
        event.setForumPostId(postId);
        event.setForumPostUrl(frontendUrl + "/Forum/Post/" + postId);
        event.setCreator(user);
        event.setCalendarSyncRequested(System.currentTimeMillis());
        em.persist(event);
        return event;
    }

    /**
     * Applies already sanitised changes to an event and its discussion post in one transaction
     * and marks the calendar entry for re-sync.
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "events-in-range"),
        @CacheInvalidateAll(cacheName = "event-years"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @WithSpan
    public Event updateEvent(Long eventId, Event changes, String postTitle, String postContent, Long editorId) {
        Telemetry.attrs("event.id", eventId, "user.id", editorId);
        Event event = em.find(Event.class, eventId);
        if (event == null) return null;
        event.setTitle(changes.getTitle());
        event.setLocation(changes.getLocation());
        event.setEventDate(changes.getEventDate());
        event.setEventEndDate(changes.getEventEndDate());
        event.setDescription(changes.getDescription());
        event.setCalendarSyncRequested(System.currentTimeMillis());
        if (event.getForumPostId() != null) {
            forumPostOrm.updateGeneratedPost(event.getForumPostId(), postTitle, postContent, editorId);
        }
        em.flush();
        return event;
    }

    /**
     * Deletes the event and its discussion post.
     *
     * @return the Google Calendar id of the deleted event (null if none or not found)
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "events-in-range"),
        @CacheInvalidateAll(cacheName = "event-years"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @WithSpan
    public String deleteEvent(Long eventId) {
        Telemetry.attr("event.id", eventId);
        Event event = em.find(Event.class, eventId);
        if (event == null) return null;
        String googleCalendarEventId = event.getGoogleCalendarEventId();
        Long postId = event.getForumPostId();
        em.remove(event);
        em.flush();
        if (postId != null) {
            forumPostOrm.deletePostById(postId);
        }
        return googleCalendarEventId;
    }

    @WithSpan
    public Event getEventByForumPostId(Long postId) {
        Telemetry.attr("forum.post.id", postId);
        TypedQuery<Event> query = em.createQuery(
                WITH_ATTENDANCES + " WHERE e.forumPostId = :postId", Event.class);
        query.setParameter("postId", postId);
        List<Event> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /** Events whose calendar entry has been waiting for a sync since before {@code requestedBefore}. */
    public List<Long> getPendingCalendarSyncIds(long requestedBefore) {
        return em.createQuery("SELECT e.id FROM Event e WHERE e.calendarSyncRequested IS NOT NULL "
                        + "AND e.calendarSyncRequested < :before ORDER BY e.id", Long.class)
                .setParameter("before", requestedBefore)
                .getResultList();
    }

    /**
     * Stores the calendar id after a successful sync. The pending marker is only cleared if the
     * event was not changed again while the sync was running.
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "events-in-range"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    public void markCalendarSynced(Long eventId, String googleCalendarEventId, Long syncedRequest) {
        em.createQuery("UPDATE Event e SET e.googleCalendarEventId = :gid, e.calendarSyncRequested = "
                        + "CASE WHEN e.calendarSyncRequested = :requested THEN NULL ELSE e.calendarSyncRequested END "
                        + "WHERE e.id = :id")
                .setParameter("gid", googleCalendarEventId)
                .setParameter("requested", syncedRequest)
                .setParameter("id", eventId)
                .executeUpdate();
    }

    /**
     * Persists or updates attendance for an event and invalidates all event caches.
     * Must be called instead of raw em.persist/merge on EventAttendance to ensure
     * cached event data stays consistent after attendance changes.
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "events-in-range"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @WithSpan
    public Event saveAttendance(Long eventId, Long userId, String status) {
        Telemetry.attrs("event.id", eventId, "user.id", userId, "event.attendance.status", status);

        Event event = em.find(Event.class, eventId);
        if (event == null) {
            return null;
        }
        User user = em.find(User.class, userId);
        if (user == null) {
            return null;
        }

        jakarta.persistence.TypedQuery<model.EventAttendance> query = em.createQuery(
                "SELECT a FROM EventAttendance a WHERE a.user.id = :userId AND a.event.id = :eventId",
                model.EventAttendance.class);
        query.setParameter("userId", userId);
        query.setParameter("eventId", eventId);

        java.util.List<model.EventAttendance> list = query.getResultList();
        if (list.isEmpty()) {
            model.EventAttendance attendance = new model.EventAttendance();
            attendance.setUser(user);
            attendance.setEvent(event);
            attendance.setStatus(status);
            em.persist(attendance);
        } else {
            model.EventAttendance attendance = list.get(0);
            attendance.setStatus(status);
            em.merge(attendance);
        }

        em.flush();
        // Attendance may have been loaded earlier in this request.
        em.refresh(event);
        return event;
    }
}
