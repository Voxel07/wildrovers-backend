package resources;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

//Logging zeug
import java.util.logging.Logger;

//HTTP Requests
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

//Eigene Imports
import orm.Secrets.SecretOrm;

@Path("/secrets")
@ApplicationScoped
public class SecretRecource {
    private static final Logger log = Logger.getLogger(SecretRecource.class.getName());

    @Inject SecretOrm secretOrm;
    @Inject orm.UserOrm userOrm;
    @Inject jakarta.persistence.EntityManager em;
    @Inject tools.AsyncMail asyncMail;
    @Inject helper.AccountSnapshotCache accountSnapshots;
    @Inject orm.AuthSessionOrm authSessions;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.password-reset.ttl", defaultValue = "30m")
    java.time.Duration resetTokenTtl;

    private static Response json(Response.Status status, String state, String message) {
        return Response.status(status).entity(jakarta.json.Json.createObjectBuilder()
                .add("status", state).add("message", message).build()).build();
    }

    public static class VerificationRequest {
        public String email;
        public String code;
    }

    @POST
    @Path("/verify")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response verifyUser(VerificationRequest request){
        log.info("SecretResource/verifyUser for email: " + (request != null ? request.email : "null"));

        if (request == null || request.email == null || request.code == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"error\", \"message\":\"E-Mail und Code müssen ausgefüllt sein\"}")
                    .build();
        }

        String result = secretOrm.verifyUser(request.email, request.code);
        if ("Erfolgreich validiert".equals(result)) {
            return json(Response.Status.OK, "success", "Erfolgreich validiert");
        }
        return json(Response.Status.BAD_REQUEST, "error", result);
    }

    @GET
    @Path("/auto-verify")
    @RolesAllowed("Admin")
    @Produces(MediaType.APPLICATION_JSON)
    @jakarta.transaction.Transactional
    public String autoVerify(@QueryParam("username") String username) {
        log.info("SecretResource/autoVerify: " + username);
        try {
            model.User user = userOrm.findByUsername(username);
            if (user != null && user.getSecret() != null) {
                user.getSecret().setIsVerifyed(true);
                em.merge(user.getSecret());
                return "{\"status\":\"Auto-verified " + username + "\"}";
            }
        } catch (Exception e) {
            return "{\"status\":\"Error: " + e.getMessage() + "\"}";
        }
        return "{\"status\":\"User not found\"}";
    }

    public static class ResetRequest {
        public String email;
    }

    public static class ResetPasswordRequest {
        public String token;
        public String password;
    }

    @POST
    @Path("/reset-request")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response requestPasswordReset(ResetRequest request) {
        log.info("SecretResource/requestPasswordReset");

        if (request == null || request.email == null || request.email.isBlank()) {
            return json(Response.Status.BAD_REQUEST, "error", "E-Mail-Adresse muss ausgefüllt sein");
        }

        // The token is committed before the mail is queued; the mail goes out on a worker thread.
        String recipient = request.email.trim();
        String token = secretOrm.createResetToken(recipient);
        if (token != null) {
            asyncMail.passwordReset(recipient, token);
        }

        // Always return success to prevent email guessing
        return json(Response.Status.OK, "success",
                "Falls diese E-Mail-Adresse registriert ist, wurde ein Link zum Zurücksetzen des Passworts gesendet.");
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @jakarta.transaction.Transactional
    public Response resetPassword(ResetPasswordRequest request) {
        log.info("SecretResource/resetPassword");

        if (request == null || request.token == null || request.token.isBlank() || request.password == null
                || request.password.length() < 8 || request.password.length() > 256) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"error\", \"message\":\"Token und neues Passwort müssen ausgefüllt sein\"}")
                    .build();
        }

        model.Users.Secret secret = secretOrm.findByResetToken(request.token.trim());
        if (secret == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"error\", \"message\":\"Ungültiger oder abgelaufener Reset-Link.\"}")
                    .build();
        }

        Long timestamp = secret.getResetTokenTimestamp();
        if (timestamp != null) {
            long elapsed = tools.Time.currentTimeInMillis() - timestamp;
            if (elapsed > resetTokenTtl.toMillis()) {
                return json(Response.Status.BAD_REQUEST, "error",
                        "Dieser Reset-Link ist abgelaufen (max. " + resetTokenTtl.toMinutes() + " Minuten gültig).");
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"error\", \"message\":\"Ungültiger Reset-Link.\"}")
                    .build();
        }

        String passwordHash = io.quarkus.elytron.security.common.BcryptUtil.bcryptHash(request.password);
        secret.setPassword(passwordHash);
        secret.setResetToken(null);
        secret.setResetTokenTimestamp(null);
        if (secret.getUser() != null) {
            // A reset proves the old credentials may be compromised: end every session.
            secret.getUser().revokeTokens();
            accountSnapshots.invalidate(secret.getUser().getId());
            authSessions.revokeAllForUser(secret.getUser().getId());
        }
        em.merge(secret);

        return Response.ok("{\"status\":\"success\", \"message\":\"Passwort erfolgreich geändert.\"}")
                .build();
    }
}
