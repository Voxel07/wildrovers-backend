package orm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import model.Event;
import model.User;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidateAll;

@ApplicationScoped
public class EventOrm {

    private static final Logger log = Logger.getLogger(EventOrm.class.getName());

    @Inject
    EntityManager em;

    @CacheResult(cacheName = "events")
    public List<Event> getAllEvents() {
        log.info("EventOrm/getAllEvents");
        TypedQuery<Event> query = em.createQuery("SELECT e FROM Event e LEFT JOIN FETCH e.creator ORDER BY e.eventDate ASC", Event.class);
        return query.getResultList();
    }

    @CacheResult(cacheName = "upcoming-events")
    public List<Event> getUpcomingEvents(int limit) {
        log.info("EventOrm/getUpcomingEvents limit=" + limit);
        LocalDateTime now = LocalDateTime.now();
        TypedQuery<Event> query = em.createQuery("SELECT e FROM Event e LEFT JOIN FETCH e.creator WHERE e.eventDate >= :now ORDER BY e.eventDate ASC", Event.class);
        query.setParameter("now", now);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @CacheResult(cacheName = "events-by-id")
    public Event getEventById(Long id) {
        log.info("EventOrm/getEventById: " + id);
        TypedQuery<Event> query = em.createQuery("SELECT e FROM Event e LEFT JOIN FETCH e.creator WHERE e.id = :id", Event.class);
        query.setParameter("id", id);
        List<Event> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    public Event addEvent(Event event, Long userId) {
        log.info("EventOrm/addEvent by user: " + userId);
        User user = em.find(User.class, userId);
        if (user == null) {
            log.warning("User not found in DB");
            throw new IllegalArgumentException("Benutzer nicht gefunden.");
        }
        event.setCreator(user);
        em.persist(event);
        return event;
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    public Event updateEvent(Event event) {
        log.info("EventOrm/updateEvent: " + event.getId());
        Event merged = em.merge(event);
        em.flush();
        return getEventById(merged.getId());
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    public void deleteEvent(Long eventId) {
        log.info("EventOrm/deleteEvent: " + eventId);
        Event event = em.find(Event.class, eventId);
        if (event != null) {
            em.remove(event);
        }
    }

    @CacheResult(cacheName = "events-by-post-id")
    public Event getEventByForumPostId(Long postId) {
        log.info("EventOrm/getEventByForumPostId: " + postId);
        TypedQuery<Event> query = em.createQuery(
                "SELECT e FROM Event e LEFT JOIN FETCH e.creator WHERE e.forumPostUrl LIKE :pattern", Event.class);
        query.setParameter("pattern", "%/Forum/Post/" + postId);
        List<Event> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }
}
