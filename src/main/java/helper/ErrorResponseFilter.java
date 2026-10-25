package helper;

import jakarta.json.Json;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Gives every error one JSON shape, {@code {"status":"error","code":"CONFLICT","message":"..."}},
 * whatever the resource returned: plain strings are wrapped, empty bodies get the
 * reason phrase, and JSON bodies are passed through with the JSON content type.
 */
@Provider
public class ErrorResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400) return;

        Object entity = response.getEntity();
        if (entity != null && !(entity instanceof String)) {
            return; // JsonObject, records, validation reports: already structured
        }
        String text = (String) entity;
        if (text != null && text.trim().startsWith("{")) {
            response.getHeaders().putSingle("Content-Type", MediaType.APPLICATION_JSON);
            return;
        }
        Response.StatusType statusInfo = Response.Status.fromStatusCode(status);
        String reason = statusInfo != null ? statusInfo.getReasonPhrase() : "Error";
        String message = text != null && !text.isBlank() ? text : defaultMessage(status, reason);
        String body = Json.createObjectBuilder()
                .add("status", "error")
                .add("code", reason.toUpperCase(java.util.Locale.ROOT).replace(' ', '_'))
                .add("message", message)
                .build()
                .toString();
        response.setEntity(body, null, MediaType.APPLICATION_JSON_TYPE);
    }

    private static String defaultMessage(int status, String reason) {
        return switch (status) {
            case 401 -> "Bitte melde dich an.";
            case 403 -> "Dafür fehlt dir die Berechtigung.";
            case 404 -> "Nicht gefunden.";
            case 429 -> "Zu viele Anfragen. Bitte warte einen Moment.";
            default -> status >= 500 ? "Interner Fehler. Bitte versuche es später erneut." : reason;
        };
    }
}
