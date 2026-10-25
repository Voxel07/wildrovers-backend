package tools;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import io.smallrye.jwt.build.Jwt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import model.Event;

@ApplicationScoped
public class GoogleCalendarService {

    private static final Logger log = Logger.getLogger(GoogleCalendarService.class.getName());

    @ConfigProperty(name = "google.calendar.credentials", defaultValue = "")
    String credentialsJson;

    @ConfigProperty(name = "google.calendar.id", defaultValue = "primary")
    String calendarId;

    /** Event times are entered as local wall-clock time of the team, not of the server. */
    @ConfigProperty(name = "notification.time-zone", defaultValue = "Europe/Berlin")
    ZoneId timeZone;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    private String cachedToken = null;
    private Instant cachedTokenExpiry = null;

    private JsonObject getCredentials() {
        String jsonStr = credentialsJson;
        if (jsonStr == null || jsonStr.isBlank()) {
            log.warning("Google Calendar credentials are not configured.");
            return null;
        }
        String trimmed = jsonStr.trim();
        if (trimmed.startsWith("@") || !trimmed.startsWith("{")) {
            String filePath = trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
            try {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    jsonStr = Files.readString(path, StandardCharsets.UTF_8);
                } else {
                    log.warning("Google Calendar credentials file does not exist: " + filePath);
                    return null;
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to read Google credentials from file: " + filePath, e);
                return null;
            }
        }
        try {
            return parseJson(jsonStr);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to parse Google credentials JSON", e);
            return null;
        }
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && cachedTokenExpiry != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }

        JsonObject creds = getCredentials();
        if (creds == null) {
            log.warning("Google Calendar credentials are not configured.");
            return null;
        }

        try {
            String clientEmail = creds.getString("client_email");
            String privateKeyPem = creds.getString("private_key");

            privateKeyPem = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);

            Instant now = Instant.now();
            String jwtAssertion = Jwt.claims()
                    .issuer(clientEmail)
                    .audience("https://oauth2.googleapis.com/token")
                    .claim("scope", "https://www.googleapis.com/auth/calendar")
                    .expiresAt(now.getEpochSecond() + 3600)
                    .issuedAt(now.getEpochSecond())
                    .sign(privateKey);

            String requestBody = "grant_type="
                    + java.net.URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                    + "&assertion=" + java.net.URLEncoder.encode(jwtAssertion, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.severe("Failed to retrieve Google OAuth token. Status: " + response.statusCode() + ", Body: "
                        + response.body());
                return null;
            }

            JsonObject respJson = parseJson(response.body());
            cachedToken = respJson.getString("access_token");
            int expiresIn = respJson.getInt("expires_in");
            cachedTokenExpiry = Instant.now().plusSeconds(expiresIn - 60); // buffer of 1 min

            return cachedToken;

        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception while obtaining Google access token", e);
            return null;
        }
    }

    /** True when credentials are set and readable, i.e. sync attempts can succeed at all. */
    public boolean isConfigured() {
        return credentialsJson != null && !credentialsJson.isBlank() && getCredentials() != null;
    }

    public String syncEvent(Event event) {
        log.info("GoogleCalendarService/syncEvent: " + event.getTitle());
        String token = getAccessToken();
        if (token == null) {
            log.warning("Skipping Google Calendar sync: service token could not be fetched.");
            return null;
        }

        try {
            JsonObject start = Json.createObjectBuilder()
                    .add("dateTime", formatIso(event.getEventDate()))
                    .add("timeZone", timeZone.getId())
                    .build();

            LocalDateTime endDateTime = event.getEventEndDate() != null ? event.getEventEndDate() : event.getEventDate().plusHours(2);
            JsonObject end = Json.createObjectBuilder()
                    .add("dateTime", formatIso(endDateTime))
                    .add("timeZone", timeZone.getId())
                    .build();

            String desc = event.getDescription() != null ? event.getDescription() : "";
            if (event.getForumPostUrl() != null && !event.getForumPostUrl().isBlank()) {
                desc += "\n\nDiskussion im Forum: " + event.getForumPostUrl();
            }

            JsonObjectBuilder body = Json.createObjectBuilder()
                    .add("summary", event.getTitle())
                    .add("description", desc)
                    .add("start", start)
                    .add("end", end);
            if (event.getLocation() != null) {
                body.add("location", event.getLocation());
            }
            JsonObject bodyJson = body.build();

            HttpRequest request;
            String eventId = event.getGoogleCalendarEventId();
            if (eventId == null || eventId.isBlank()) {
                // Create
                request = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/" + calendarId + "/events"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson.toString()))
                        .build();
            } else {
                // Update
                request = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/" + calendarId + "/events/"
                                + eventId))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(bodyJson.toString()))
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonObject resp = parseJson(response.body());
                return resp.getString("id");
            } else {
                log.severe("Google Calendar API request failed. Status: " + response.statusCode() + ", Body: "
                        + response.body());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to sync event to Google Calendar", e);
        }
        return null;
    }

    public void deleteEvent(String googleCalendarEventId) {
        log.info("GoogleCalendarService/deleteEvent: " + googleCalendarEventId);
        if (googleCalendarEventId == null || googleCalendarEventId.isBlank()) {
            return;
        }

        String token = getAccessToken();
        if (token == null) {
            log.warning("Skipping Google Calendar deletion: service token could not be fetched.");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/" + calendarId + "/events/"
                            + googleCalendarEventId))
                    .header("Authorization", "Bearer " + token)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 204 && response.statusCode() != 410
                    && response.statusCode() != 404) {
                log.severe("Google Calendar event deletion failed. Status: " + response.statusCode() + ", Body: "
                        + response.body());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to delete event from Google Calendar", e);
        }
    }

    private String formatIso(LocalDateTime dateTime) {
        return dateTime.atZone(timeZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static JsonObject parseJson(String body) {
        try (JsonReader reader = Json.createReader(new java.io.StringReader(body))) {
            return reader.readObject();
        }
    }
}
