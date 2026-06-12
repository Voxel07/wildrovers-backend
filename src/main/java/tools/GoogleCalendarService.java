package tools;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.json.JSONObject;
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

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    private String cachedToken = null;
    private Instant cachedTokenExpiry = null;

    private JSONObject getCredentials() {
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
            return new JSONObject(jsonStr);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to parse Google credentials JSON", e);
            return null;
        }
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && cachedTokenExpiry != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }

        JSONObject creds = getCredentials();
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

            JSONObject respJson = new JSONObject(response.body());
            cachedToken = respJson.getString("access_token");
            int expiresIn = respJson.getInt("expires_in");
            cachedTokenExpiry = Instant.now().plusSeconds(expiresIn - 60); // buffer of 1 min

            return cachedToken;

        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception while obtaining Google access token", e);
            return null;
        }
    }

    public String syncEvent(Event event) {
        log.info("GoogleCalendarService/syncEvent: " + event.getTitle());
        String token = getAccessToken();
        if (token == null) {
            log.warning("Skipping Google Calendar sync: service token could not be fetched.");
            return null;
        }

        try {
            JSONObject start = new JSONObject()
                    .put("dateTime", formatIso(event.getEventDate()))
                    .put("timeZone", ZoneId.systemDefault().getId());

            // Default event duration: 2 hours
            JSONObject end = new JSONObject()
                    .put("dateTime", formatIso(event.getEventDate().plusHours(2)))
                    .put("timeZone", ZoneId.systemDefault().getId());

            String desc = event.getDescription() != null ? event.getDescription() : "";
            if (event.getForumPostUrl() != null && !event.getForumPostUrl().isBlank()) {
                desc += "\n\nDiskussion im Forum: " + event.getForumPostUrl();
            }

            JSONObject bodyJson = new JSONObject()
                    .put("summary", event.getTitle())
                    .put("description", desc)
                    .put("location", event.getLocation())
                    .put("start", start)
                    .put("end", end);

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
                JSONObject resp = new JSONObject(response.body());
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
        return dateTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
