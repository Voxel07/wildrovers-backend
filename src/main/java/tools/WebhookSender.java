package tools;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@ApplicationScoped
public class WebhookSender {
    @ConfigProperty(name = "notification.webhook.require-https", defaultValue = "true") boolean requireHttps;
    @ConfigProperty(name = "notification.webhook.timeout-seconds", defaultValue = "8") int timeoutSeconds;

    public void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Ungültige Webhook-URL");
            }
            if (requireHttps && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Webhook-URLs müssen HTTPS verwenden");
            }
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Nur HTTP(S)-Webhook-URLs sind erlaubt");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Private oder lokale Webhook-Ziele sind nicht erlaubt");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook-Ziel konnte nicht validiert werden", e);
        }
    }

    public int send(String url, String secret, String eventType, String body, String deliveryId) throws Exception {
        validateUrl(url); // Re-resolve for every delivery to reduce DNS-rebinding risk.
        String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
        String signature = sign(secret, timestamp + "." + body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("User-Agent", "WildRovers-Notifications/1.0")
                .header("X-Wildrovers-Event", eventType)
                .header("X-Wildrovers-Delivery", deliveryId == null ? UUID.randomUUID().toString() : deliveryId)
                .header("X-Wildrovers-Timestamp", timestamp)
                .header("X-Wildrovers-Signature", "sha256=" + signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String sign(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
