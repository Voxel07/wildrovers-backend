package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.AppSettings;

import java.util.logging.Logger;

/**
 * Application-scoped service for reading and writing global app settings.
 * Persists settings to the APP_SETTINGS table (single row, id = 1).
 *
 * On first startup Hibernate creates the table (schema-management.strategy=update)
 * and the first call to getSettings() seeds the row with defaults.
 */
@ApplicationScoped
public class SignupSettings {

    private static final Logger log = Logger.getLogger(SignupSettings.class.getName());
    private static final Long SETTINGS_ID = 1L;

    @Inject
    EntityManager em;

    /**
     * Returns the persisted settings row, creating it with defaults if absent.
     */
    @Transactional
    public AppSettings getSettings() {
        AppSettings settings = em.find(AppSettings.class, SETTINGS_ID);
        if (settings == null) {
            log.info("SignupSettings: no settings row found, creating defaults.");
            settings = new AppSettings();
            settings.setId(SETTINGS_ID);
            settings.setSignupEnabled(true);
            em.persist(settings);
        }
        return settings;
    }

    public boolean isSignupEnabled() {
        return getSettings().isSignupEnabled();
    }

    @Transactional
    public void setSignupEnabled(boolean enabled) {
        log.info("SignupSettings: setting signupEnabled = " + enabled);
        AppSettings settings = em.find(AppSettings.class, SETTINGS_ID);
        if (settings == null) {
            settings = new AppSettings();
            settings.setId(SETTINGS_ID);
        }
        settings.setSignupEnabled(enabled);
        em.merge(settings);
    }
}
