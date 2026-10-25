package tools;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class WebhookCrypto {
    @ConfigProperty(name = "notification.webhook.encryption-key") String configuredKey;

    public String encrypt(String value) {
        try {
            return AesGcm.encrypt(configuredKey, value);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook-Daten konnten nicht verschlüsselt werden", e);
        }
    }

    public String decrypt(String value) {
        try {
            return AesGcm.decrypt(configuredKey, value);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook-Daten konnten nicht entschlüsselt werden", e);
        }
    }

    public String newSecret() {
        return AesGcm.randomToken();
    }
}
