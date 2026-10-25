package tools;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import model.Event;
import orm.EventOrm;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Google Calendar sync outside of database transactions (outbox pattern). Event writes only set
 * {@code calendar_sync_requested}; this service pushes the event to Google after the commit and
 * a scheduler retries entries that failed (Google down, credentials missing, restart).
 */
@ApplicationScoped
public class EventCalendarSync {

    /** The request thread syncs right after the commit; the retry leaves those alone for a while. */
    private static final long RETRY_GRACE_MILLIS = 2 * 60 * 1000L;

    @Inject
    EventOrm eventOrm;

    @Inject
    GoogleCalendarService googleCalendarService;

    /**
     * Pushes a pending event to Google Calendar. Synchronized so that the request thread and the
     * retry job can never create two calendar entries for the same event.
     */
    public synchronized void sync(Long eventId) {
        Event event = eventOrm.findUncached(eventId);
        if (event == null || event.getCalendarSyncRequested() == null) return;
        Long requested = event.getCalendarSyncRequested();
        try {
            String googleId = googleCalendarService.syncEvent(event);
            if (googleId != null) {
                eventOrm.markCalendarSynced(eventId, googleId, requested);
            }
        } catch (Exception e) {
            Telemetry.error("Calendar sync failed; will retry", e, "event.id", eventId);
        }
    }

    /** Called after the event row is gone; a failure is only logged. */
    public void delete(String googleCalendarEventId) {
        if (googleCalendarEventId == null || googleCalendarEventId.isBlank()) return;
        try {
            googleCalendarService.deleteEvent(googleCalendarEventId);
        } catch (Exception e) {
            Telemetry.error("Calendar delete failed", e, "google.calendar.event.id", googleCalendarEventId);
        }
    }

    @Scheduled(every = "${events.calendar.retry-interval:5m}", delayed = "1m", concurrentExecution = SKIP)
    void retryPending() {
        if (!googleCalendarService.isConfigured()) return;
        for (Long id : eventOrm.getPendingCalendarSyncIds(System.currentTimeMillis() - RETRY_GRACE_MILLIS)) {
            sync(id);
        }
    }
}
