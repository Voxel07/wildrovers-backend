package tools;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import io.opentelemetry.instrumentation.annotations.WithSpan;


/**
 * Sends account mails off the request thread. The caller's response then takes
 * the same time whether or not a mail goes out, so it does not reveal which
 * addresses are registered, and slow SMTP servers never block a request or hold
 * a database connection.
 * <p>
 * When called inside a transaction, the mail is queued only after that transaction
 * has committed, so a rolled-back sign-up never sends a code.
 */
@ApplicationScoped
public class AsyncMail {

    @Inject
    Email email;

    @Inject
    AsyncMail self;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    @WithSpan
    public void passwordReset(String recipient, String token) {
        afterCommit(() -> self.send("password reset", () -> email.sendPasswordResetMail(recipient, token)));
    }

    @WithSpan
    public void verification(String recipient, String code) {
        afterCommit(() -> self.send("verification", () -> email.sendVerificationMail(recipient, code)));
    }

    @WithSpan
    public void emailChange(String recipient, String code) {
        afterCommit(() -> self.send("e-mail change", () -> email.sendEmailChangeMail(recipient, code)));
    }

    @WithSpan
    public void accountExists(String recipient) {
        afterCommit(() -> self.send("account exists", () -> email.sendAccountExistsMail(recipient)));
    }

    private void afterCommit(Runnable task) {
        Runnable dispatch = () -> Infrastructure.getDefaultWorkerPool().execute(task);
        int status;
        try {
            status = txRegistry.getTransactionStatus();
        } catch (RuntimeException e) {
            status = Status.STATUS_NO_TRANSACTION;
        }
        if (status != Status.STATUS_ACTIVE) {
            dispatch.run();
            return;
        }
        txRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() { }

            @Override
            public void afterCompletion(int completion) {
                if (completion == Status.STATUS_COMMITTED) {
                    dispatch.run();
                }
            }
        });
    }

    @ActivateRequestContext
    @WithSpan
    void send(String kind, Runnable mail) {
        try {
            mail.run();
        } catch (Exception e) {
            Telemetry.error("Sending " + kind + " e-mail failed", e, "email.kind", kind);
        }
    }
}
