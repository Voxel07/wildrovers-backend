package tools;

import java.util.logging.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Audit logger for CRUD operations using OpenTelemetry spans.
 * Spans appear under <strong>Traces</strong> in OpenObserve, filterable by
 * {@code audit.action}, {@code audit.entity}, {@code audit.user}, etc.
 */
public final class AuditLogger {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("wildrovers-audit");

    private AuditLogger() {
        // utility class
    }

    /**
     * Record a CRUD operation as an OTel span.
     *
     * @param log      the class-level logger (for fallback / debug)
     * @param userName the username performing the action
     * @param userId   the user id
     * @param action   CREATE, UPDATE, DELETE, VOTE, TOGGLE, ATTEND, REGISTER
     * @param entity   entity type (e.g. "Category", "Post", "Event")
     * @param entityId the entity id (may be null)
     * @param details  additional context (may be null)
     */
    public static void crud(Logger log, String userName, Long userId,
                            String action, String entity, Object entityId,
                            String details) {
        Span span = tracer.spanBuilder(entity + " " + action)
                .setAttribute("audit.action", action)
                .setAttribute("audit.entity", entity)
                .setAttribute("audit.user", userName != null ? userName : "SYSTEM")
                .setAttribute("audit.userId", userId != null ? userId : 0)
                .startSpan();

        if (entityId != null) {
            span.setAttribute("audit.entityId", String.valueOf(entityId));
        }
        if (details != null && !details.isBlank()) {
            span.setAttribute("audit.details", details);
        }

        span.setStatus(StatusCode.OK);
        span.end();

        // Also log for file-based debugging
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
