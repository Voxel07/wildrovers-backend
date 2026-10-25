package tools;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends account mails off the request thread. The caller's response then takes
 * the same time whether or not a mail goes out, so it does not reveal which
 * addresses are registered, and slow SMTP servers never block a request.
 */
@ApplicationScoped
public class AsyncMail {
    private static final Logger log = Logger.getLogger(AsyncMail.class.getName());

    @Inject
    Email email;

    @Inject
    AsyncMail self;

    public void passwordReset(String recipient, String token) {
        Infrastructure.getDefaultWorkerPool().execute(() -> self.sendPasswordReset(recipient, token));
    }

    @ActivateRequestContext
    void sendPasswordReset(String recipient, String token) {
        try {
            email.sendPasswordResetMail(recipient, token);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Fehler beim Senden der Passwort-Reset-E-Mail: " + e.getMessage(), e);
        }
    }
}
