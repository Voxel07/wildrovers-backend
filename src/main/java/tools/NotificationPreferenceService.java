package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.User;
import model.notifications.NotificationPreference;
import model.notifications.UserWebhook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class NotificationPreferenceService {
    public static final List<String> RESOURCE_TYPES = List.of("EVENT", "FORUM", "GALLERY");
    @Inject EntityManager em;
    @Inject WebhookCrypto crypto;
    @Inject WebhookSender webhookSender;

    public List<NotificationPreference> getPreferences(Long userId) {
        return em.createQuery("SELECT p FROM NotificationPreference p WHERE p.user.id = :userId", NotificationPreference.class)
                .setParameter("userId", userId).getResultList();
    }

    public UserWebhook getWebhook(Long userId) {
        List<UserWebhook> result = em.createQuery("SELECT w FROM UserWebhook w WHERE w.user.id = :userId", UserWebhook.class)
                .setParameter("userId", userId).getResultList();
        return result.isEmpty() ? null : result.get(0);
    }

    @Transactional
    public String save(Long userId, Map<String, ChannelSelection> selections, String webhookUrl) {
        User user = em.find(User.class, userId);
        if (user == null) throw new IllegalArgumentException("Benutzer nicht gefunden");
        boolean wantsWebhook = selections.values().stream().anyMatch(ChannelSelection::webhook);
        UserWebhook webhook = getWebhook(userId);
        String newSecret = null;
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            webhookSender.validateUrl(webhookUrl.trim());
            String currentUrl = webhook == null ? null : crypto.decrypt(webhook.getUrlEncrypted());
            if (webhook == null) {
                webhook = new UserWebhook();
                webhook.setUser(user);
                newSecret = crypto.newSecret();
                webhook.setSecretEncrypted(crypto.encrypt(newSecret));
                em.persist(webhook);
            } else if (!webhookUrl.trim().equals(currentUrl)) {
                webhook.setVerifiedAt(null);
            }
            webhook.setUrlEncrypted(crypto.encrypt(webhookUrl.trim()));
            webhook.setEnabled(true);
        }
        if (wantsWebhook && webhook == null) throw new IllegalArgumentException("Bitte zuerst eine Webhook-URL angeben");

        long now = System.currentTimeMillis();
        Map<String, NotificationPreference> existing = new HashMap<>();
        getPreferences(userId).forEach(p -> existing.put(p.getResourceType(), p));
        for (String type : RESOURCE_TYPES) {
            ChannelSelection selected = selections.getOrDefault(type, new ChannelSelection(false, false));
            NotificationPreference preference = existing.get(type);
            if (preference == null) {
                preference = new NotificationPreference();
                preference.setUser(user);
                preference.setResourceType(type);
                em.persist(preference);
            }
            preference.setEmailEnabled(selected.email());
            preference.setWebhookEnabled(selected.webhook());
            preference.setUpdatedAt(now);
        }
        return newSecret;
    }

    @Transactional
    public String rotateSecret(Long userId) {
        UserWebhook webhook = getWebhook(userId);
        if (webhook == null) throw new IllegalArgumentException("Kein Webhook konfiguriert");
        String secret = crypto.newSecret();
        webhook.setSecretEncrypted(crypto.encrypt(secret));
        webhook.setVerifiedAt(null);
        return secret;
    }

    @Transactional
    public void deleteWebhook(Long userId) {
        UserWebhook webhook = getWebhook(userId);
        if (webhook != null) em.remove(webhook);
        getPreferences(userId).forEach(p -> p.setWebhookEnabled(false));
    }

    public WebhookTarget target(Long userId) {
        UserWebhook webhook = getWebhook(userId);
        if (webhook == null || !webhook.isEnabled()) return null;
        if (webhook.getDisabledUntil() != null && webhook.getDisabledUntil() > System.currentTimeMillis()) return null;
        return new WebhookTarget(webhook.getId(), crypto.decrypt(webhook.getUrlEncrypted()), crypto.decrypt(webhook.getSecretEncrypted()));
    }

    @Transactional
    public void recordWebhookResult(Long webhookId, boolean success, String error) {
        UserWebhook webhook = em.find(UserWebhook.class, webhookId);
        if (webhook == null) return;
        long now = System.currentTimeMillis();
        if (success) {
            if (webhook.getVerifiedAt() == null) webhook.setVerifiedAt(now);
            webhook.setLastSuccessAt(now);
            webhook.setFailureCount(0);
            webhook.setDisabledUntil(null);
            webhook.setLastError(null);
        } else {
            webhook.setLastFailureAt(now);
            webhook.setFailureCount(webhook.getFailureCount() + 1);
            String safeError = error == null ? "Webhook fehlgeschlagen" : error;
            webhook.setLastError(safeError.substring(0, Math.min(500, safeError.length())));
            if (webhook.getFailureCount() >= 6) webhook.setDisabledUntil(now + 86_400_000L);
        }
    }

    public record ChannelSelection(boolean email, boolean webhook) {}
    public record WebhookTarget(Long id, String url, String secret) {}
}
