package tools;

import java.util.logging.Logger;

/**
 * Structured audit logger for CRUD operations.
 * Logs who performed what action on which entity, formatted for OpenObserve / OTel ingestion.
 * <p>
 * Format: {@code AUDIT | user=USERNAME | userId=ID | action=CREATE|UPDATE|DELETE | entity=TYPE | entityId=ID | details=...}
 */
public final class AuditLogger {

    private AuditLogger() {
        // utility class
    }

    /**
     * Log a CRUD operation with user context.
     *
     * @param log      the class-level logger
     * @param userName the username performing the action
     * @param userId   the user id
     * @param action   CREATE, UPDATE, DELETE, VOTE, TOGGLE
     * @param entity   entity type (e.g. "Category", "Post", "Event")
     * @param entityId the entity id (may be null)
     * @param details  additional context (may be null)
     */
    public static void crud(Logger log, String userName, Long userId,
                            String action, String entity, Object entityId,
                            String details) {
        StringBuilder sb = new StringBuilder("AUDIT | user=").append(userName)
                .append(" | userId=").append(userId)
                .append(" | action=").append(action)
                .append(" | entity=").append(entity);
        if (entityId != null) {
            sb.append(" | entityId=").append(entityId);
        }
        if (details != null && !details.isBlank()) {
            sb.append(" | details=").append(details);
        }
        log.info(sb.toString());
    }

    /** Convenience overload without details. */
    public static void crud(Logger log, String userName, Long userId,
                            String action, String entity, Object entityId) {
        crud(log, userName, userId, action, entity, entityId, null);
    }

    /** Convenience overload with only action + entity + entityId (no user). */
    public static void system(Logger log, String action, String entity, Object entityId, String details) {
        crud(log, "SYSTEM", null, action, entity, entityId, details);
    }
}
