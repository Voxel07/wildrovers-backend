package tools;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class NotificationScheduler {
    private static final Logger log = Logger.getLogger(NotificationScheduler.class.getName());
    @Inject NotificationService notifications;
    @Inject NotificationPreferenceService preferences;
    @Inject Email email;
    @Inject WebhookSender webhookSender;
    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173") String frontendUrl;

    @Scheduled(every = "${notification.mail.delivery-interval:90s}", concurrentExecution = SKIP)
    void deliverImmediateEvents() {
        java.util.List<NotificationService.DeliveryBatch> emailEvents = notifications.dueEventBatches("EMAIL");
        if (!emailEvents.isEmpty()) {
            deliver(emailEvents.subList(0, 1), "EMAIL", "Wild Rovers - Event-Benachrichtigung", "event.notification");
        } else {
            java.util.List<NotificationService.DeliveryBatch> digests = notifications.dueDigestBatches("EMAIL");
            if (!digests.isEmpty()) deliver(digests.subList(0, 1), "EMAIL", "Wild Rovers - Verpasste Neuigkeiten", "notification.digest");
        }
        deliver(notifications.dueEventBatches("WEBHOOK"), "WEBHOOK", null, "event.notification");
        deliver(notifications.dueDigestBatches("WEBHOOK"), "WEBHOOK", null, "notification.digest");
    }

    @Scheduled(cron = "${notification.digest.cron:0 0 6 * * ?}", timeZone = "${notification.time-zone:Europe/Berlin}", concurrentExecution = SKIP)
    void deliverDailyDigest() {
        // The frequent worker drains due digests gradually so Zoho never receives
        // a 06:00 burst. This trigger remains as an explicit scheduler/health marker.
    }

    private void deliver(java.util.List<NotificationService.DeliveryBatch> batches, String channel, String subject, String eventType) {
        for (NotificationService.DeliveryBatch batch : batches) {
            try {
                boolean sent;
                if ("EMAIL".equals(channel)) {
                    sent = email.trySendNotificationMail(batch.email(), subject, html(batch));
                } else {
                    NotificationPreferenceService.WebhookTarget target = preferences.target(batch.userId());
                    if (target == null) continue;
                    String deliveryId = UUID.randomUUID().toString();
                    int status = webhookSender.send(target.url(), target.secret(), eventType, json(batch, deliveryId, eventType), deliveryId);
                    sent = status >= 200 && status < 300;
                    preferences.recordWebhookResult(target.id(), sent, "HTTP " + status);
                }
                if (sent) notifications.markDelivered(batch.receiptIds(), channel);
                else notifications.markRetry(batch.receiptIds(), channel, batch.attempts());
            } catch (Exception e) {
                log.log(Level.WARNING, channel + " notification delivery failed for user " + batch.userId(), e);
                notifications.markRetry(batch.receiptIds(), channel, batch.attempts());
            }
        }
    }

    private String html(NotificationService.DeliveryBatch batch) {
        StringBuilder rows = new StringBuilder();
        for (NotificationService.DeliveryItem item : batch.items()) {
            rows.append("<li><strong>").append(escape(item.title())).append("</strong>");
            if (item.url() != null) rows.append(" - <a href=\"").append(escape(item.url())).append("\">Ansehen</a>");
            rows.append("</li>");
        }
        return "<!doctype html><html><body><h2>Wild Rovers</h2><p>Hallo " + escape(batch.username())
                + ",</p><p>du hast folgende Neuigkeiten:</p><ul>" + rows + "</ul><p><a href=\"" + escape(frontendUrl)
                + "/Profil\">Benachrichtigungen verwalten und als gelesen markieren</a></p></body></html>";
    }

    private String json(NotificationService.DeliveryBatch batch, String deliveryId, String type) {
        StringBuilder items = new StringBuilder();
        for (NotificationService.DeliveryItem item : batch.items()) {
            if (!items.isEmpty()) items.append(',');
            items.append("{\"id\":").append(item.id()).append(",\"resource\":\"").append(jsonEscape(item.resource()))
                    .append("\",\"action\":\"").append(jsonEscape(item.action())).append("\",\"entityId\":").append(item.entityId())
                    .append(",\"title\":\"").append(jsonEscape(item.title())).append("\",\"url\":")
                    .append(item.url() == null ? "null" : "\"" + jsonEscape(item.url()) + "\"").append('}');
        }
        return "{\"id\":\"" + deliveryId + "\",\"type\":\"" + type + "\",\"createdAt\":"
                + System.currentTimeMillis() + ",\"notifications\":[" + items + "]}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
