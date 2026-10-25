package tools;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * Records diagnostics on the current OpenTelemetry span instead of a log file.
 * Request spans are created by the Quarkus REST instrumentation, ORM and service
 * methods add child spans via {@code @WithSpan}; this class enriches whichever
 * span is active. Without an active span (OTel disabled, background thread
 * outside a span) every call is a no-op.
 *
 * <p>Key/value arguments are passed as alternating pairs:
 * {@code Telemetry.warn("Quota exceeded", "user.id", id, "quota.limit", 50)}.
 * Null values are skipped.
 */
public final class Telemetry {

    /** Longer string values (e.g. upstream error bodies) are cut to keep spans small. */
    private static final int MAX_STRING_LENGTH = 2048;

    private Telemetry() {
        // utility class
    }

    /** Set an attribute on the current span. Null values are ignored. */
    public static void attr(String key, Object value) {
        Span span = Span.current();
        if (value == null || !span.isRecording()) return;
        switch (value) {
            case Long l -> span.setAttribute(key, l);
            case Integer i -> span.setAttribute(key, i.longValue());
            case Boolean b -> span.setAttribute(key, b);
            case Double d -> span.setAttribute(key, d);
            default -> span.setAttribute(key, truncate(String.valueOf(value)));
        }
    }

    /** Set several attributes on the current span. */
    public static void attrs(Object... keyValues) {
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            attr(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
    }

    /** Add a named event (a point in time inside the span) with attributes. */
    public static void event(String name, Object... keyValues) {
        Span span = Span.current();
        if (!span.isRecording()) return;
        span.addEvent(name, toAttributes(keyValues));
    }

    /** Something unexpected but handled: recorded as a {@code warning} event. */
    public static void warn(String message, Object... keyValues) {
        Span span = Span.current();
        if (!span.isRecording()) return;
        span.addEvent("warning", toAttributes(keyValues).toBuilder()
                .put("message", message)
                .build());
    }

    /** A handled exception that did not fail the operation. */
    public static void warn(String message, Throwable error, Object... keyValues) {
        Span span = Span.current();
        if (!span.isRecording()) return;
        span.recordException(error, toAttributes(keyValues).toBuilder()
                .put("message", message)
                .put("severity", "warning")
                .build());
    }

    /** A failure: recorded as an error event and marks the span as failed. */
    public static void error(String message, Object... keyValues) {
        Span span = Span.current();
        if (!span.isRecording()) return;
        span.addEvent("error", toAttributes(keyValues).toBuilder()
                .put("message", message)
                .build());
        span.setStatus(StatusCode.ERROR, message);
    }

    /** A failure with its exception: records the stack trace and marks the span as failed. */
    public static void error(String message, Throwable error, Object... keyValues) {
        Span span = Span.current();
        if (!span.isRecording()) return;
        span.recordException(error, toAttributes(keyValues).toBuilder()
                .put("message", message)
                .put("severity", "error")
                .build());
        span.setStatus(StatusCode.ERROR, message);
    }

    static Attributes toAttributes(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) return Attributes.empty();
        AttributesBuilder builder = Attributes.builder();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            switch (keyValues[i + 1]) {
                case null -> { }
                case Long l -> builder.put(key, l);
                case Integer n -> builder.put(key, n.longValue());
                case Boolean b -> builder.put(key, b);
                case Double d -> builder.put(key, d);
                case Object o -> builder.put(key, truncate(String.valueOf(o)));
            }
        }
        return builder.build();
    }

    private static String truncate(String value) {
        return value.length() <= MAX_STRING_LENGTH ? value : value.substring(0, MAX_STRING_LENGTH) + "…";
    }
}
