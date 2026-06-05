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

@ApplicationScoped
public class EventOrm {

    private static final Logger log = Logger.getLogger(EventOrm.class.getName());

    @Inject
    EntityManager em;

    public List<Event> getAllEvents() {
        log.info("EventOrm/getAllEvents");
        TypedQuery<Event> query = em.createQuery("SELECT e FROM Event e LEFT JOIN FETCH e.creator ORDER BY e.eventDate ASC", Event.class);
        return query.getResultList();
    }

    public List<Event> getUpcomingEvents(int limit) {
        log.info("EventOrm/getUpcomingEvents limit=" + limit);
        LocalDateTime now = LocalDateTime.now();
        TypedQuery<Event> query = em.createQuery("SELECT e FROM Event e LEFT JOIN FETCH e.creator WHERE e.eventDate >= :now ORDER BY e.eventDate ASC", Event.class);
        query.setParameter("now", now);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public Event getEventById(Long id) {
        log.info("EventOrm/getEventById: " + id);
        TypedQuery<Event> query = em.createQuery("SELECT e FROM Event e LEFT JOIN FETCH e.creator WHERE e.id = :id", Event.class);
        query.setParameter("id", id);
        List<Event> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Transactional
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
    public Event updateEvent(Event event) {
        log.info("EventOrm/updateEvent: " + event.getId());
        Event merged = em.merge(event);
        em.flush();
        return getEventById(merged.getId());
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        log.info("EventOrm/deleteEvent: " + eventId);
        Event event = em.find(Event.class, eventId);
        if (event != null) {
            em.remove(event);
        }
    }
}
