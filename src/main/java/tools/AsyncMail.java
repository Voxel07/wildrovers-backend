package tools;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import io.opentelemetry.instrumentation.annotations.WithSpan;


/**
 * Sends account mails off the request thread. The caller's response then takes
 * the same time whether or not a mail goes out, so it does not reveal which
 * addresses are registered, and slow SMTP servers never block a request.
 */
@ApplicationScoped
public class AsyncMail {

    @Inject
    Email email;

    @Inject
    AsyncMail self;

    public void passwordReset(String recipient, String token) {
        Infrastructure.getDefaultWorkerPool().execute(() -> self.sendPasswordReset(recipient, token));
    }

    @ActivateRequestContext
    @WithSpan
    void sendPasswordReset(String recipient, String token) {
        try {
            email.sendPasswordResetMail(recipient, token);
        } catch (Exception e) {
            Telemetry.error("Sending password reset e-mail failed", e);
        }
    }
}
