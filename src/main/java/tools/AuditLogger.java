package tools;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.Arc;

/**
 * Audit trail for CRUD operations using OpenTelemetry spans.
 * Each operation becomes its own {@code "<Entity> <ACTION>"} span, a child of the
 * request span, so it appears under <strong>Traces</strong> in OpenObserve with
 * the HTTP route, client address and timing of the request that caused it.
 * The request span is tagged with the same {@code audit.*} attributes and an
 * {@code audit} event, so requests can be filtered by what they changed.
 */
public final class AuditLogger {

    private static final AttributeKey<String> ACTION = AttributeKey.stringKey("audit.action");
    private static final AttributeKey<String> ENTITY = AttributeKey.stringKey("audit.entity");
    private static final AttributeKey<String> ENTITY_ID = AttributeKey.stringKey("audit.entityId");
    private static final AttributeKey<String> USER = AttributeKey.stringKey("audit.user");
    private static final AttributeKey<Long> USER_ID = AttributeKey.longKey("audit.userId");
    private static final AttributeKey<String> DETAILS = AttributeKey.stringKey("audit.details");
    private static final AttributeKey<Boolean> SYSTEM = AttributeKey.booleanKey("audit.system");
    private static final AttributeKey<String> ENDUSER_ID = AttributeKey.stringKey("enduser.id");

    private AuditLogger() {
        // utility class
    }

    /**
     * Record a CRUD operation as an OTel span.
     *
     * @param userName the username performing the action
     * @param userId   the user id
     * @param action   CREATE, UPDATE, DELETE, VOTE, TOGGLE, ATTEND, REGISTER
     * @param entity   entity type (e.g. "Category", "Post", "Event")
     * @param entityId the entity id (may be null)
     * @param details  additional context (may be null)
     */
    public static void crud(String userName, Long userId,
                            String action, String entity, Object entityId,
                            String details) {
        boolean system = userName == null || "SYSTEM".equals(userName);

        AttributesBuilder builder = Attributes.builder()
                .put(ACTION, action)
                .put(ENTITY, entity)
                .put(USER, system ? "SYSTEM" : userName)
                .put(SYSTEM, system);
        if (userId != null) {
            builder.put(USER_ID, userId);
            builder.put(ENDUSER_ID, String.valueOf(userId));
        }
        if (entityId != null) {
            builder.put(ENTITY_ID, String.valueOf(entityId));
        }
        if (details != null && !details.isBlank()) {
            builder.put(DETAILS, details);
        }
        Attributes attributes = builder.build();

        Span parent = Span.current();
        if (parent.isRecording()) {
            parent.setAllAttributes(attributes);
            parent.addEvent("audit", attributes);
        }

        // Resolve the managed SDK only at runtime. A static global tracer would
        // register a no-op SDK during native-image class initialization.
        Tracer tracer = Arc.container().instance(OpenTelemetry.class).get().getTracer("wildrovers-audit");
        Span span = tracer.spanBuilder(entity + " " + action)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attributes)
                .startSpan();
        span.setStatus(StatusCode.OK);
        span.end();
    }

    /** Convenience overload without details. */
    public static void crud(String userName, Long userId,
                            String action, String entity, Object entityId) {
        crud(userName, userId, action, entity, entityId, null);
    }

    /** Convenience overload with only action + entity + entityId (no user). */
    public static void system(String action, String entity, Object entityId, String details) {
        crud("SYSTEM", null, action, entity, entityId, details);
    }
}
