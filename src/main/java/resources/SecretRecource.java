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

//Eigene Imports
import orm.Secrets.SecretOrm;

@Path("/secrets")
@ApplicationScoped
public class SecretRecource {
    private static final Logger log = Logger.getLogger(SecretRecource.class.getName());

    @Inject SecretOrm secretOrm;
    @Inject orm.UserOrm userOrm;
    @Inject jakarta.persistence.EntityManager em;
    @Inject tools.Email email;

    public static class VerificationRequest {
        public String email;
        public String code;
    }

    @POST
    @Path("/verify")
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
            return Response.ok("{\"status\":\"success\", \"message\":\"Erfolgreich validiert\"}").build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"error\", \"message\":\"" + result + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/auto-verify")
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
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @jakarta.transaction.Transactional
    public Response requestPasswordReset(ResetRequest request) {
        log.info("SecretResource/requestPasswordReset for email: " + (request != null ? request.email : "null"));

        if (request == null || request.email == null || request.email.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"status\":\"error\", \"message\":\"E-Mail-Adresse muss ausgefüllt sein\"}")
                    .build();
        }

        model.User user = userOrm.findByEmail(request.email.trim());
        if (user != null && user.getSecret() != null) {
            String token = java.util.UUID.randomUUID().toString();
            user.getSecret().setResetToken(token);
            user.getSecret().setResetTokenTimestamp(tools.Time.currentTimeInMillis());
            em.merge(user.getSecret());
            try {
                email.sendPasswordResetMail(user.getEmail(), token);
            } catch (Exception e) {
                log.log(java.util.logging.Level.SEVERE, "Fehler beim Senden der Passwort-Reset-E-Mail: " + e.getMessage(), e);
            }
        }

        // Always return success to prevent email guessing
        return Response.ok("{\"status\":\"success\", \"message\":\"Falls diese E-Mail-Adresse registriert ist, wurde ein Link zum Zurücksetzen des Passworts gesendet.\"}")
                .build();
    }

    @POST
    @Path("/reset-password")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @jakarta.transaction.Transactional
    public Response resetPassword(ResetPasswordRequest request) {
        log.info("SecretResource/resetPassword");

        if (request == null || request.token == null || request.token.isBlank() || request.password == null || request.password.isBlank()) {
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
            if (elapsed > 15 * 60 * 1000) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"status\":\"error\", \"message\":\"Dieser Reset-Link ist abgelaufen.\"}")
                        .build();
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
        em.merge(secret);

        return Response.ok("{\"status\":\"success\", \"message\":\"Passwort erfolgreich geändert.\"}")
                .build();
    }
}
