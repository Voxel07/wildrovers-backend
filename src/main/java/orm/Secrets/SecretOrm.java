package orm.Secrets;

import model.Users.Secret;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

import model.User;
import orm.UserOrm;
//Logging
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;

@ApplicationScoped
public class SecretOrm {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    UserOrm userOrm;

    @Inject
    EntityManager em;

    @WithSpan
    public String addSecret(Long userId, Boolean isVerifyed, String verificationId, String password){
        User user = em.find(User.class, userId);

        if(user == null){
            Telemetry.warn("User not found", "user.id", userId);
            return "User nicht gefunden";
        }

        Secret secret = new Secret(isVerifyed,verificationId);
        secret.setPassword(password);
        secret.setUser(user);
        secret.setVerificationTimestamp(tools.Time.currentTimeInMillis());

        try {
            em.persist(secret);
        } catch (Exception e) {
            Telemetry.error("Storing secret failed", e, "user.id", userId);
            return "Fehler beim erstellen des Eintrags";
        }
        return "Secreat erfolgreich erstellt";
    }

    public String generateVerificationId()
    {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    @Transactional
    @WithSpan
    public String verifyUser(String email, String verificationId)
    {

        User user = userOrm.findByEmail(email);
        if (user == null) {
            Telemetry.event("verification.user_not_found");
            return "Der User wurde nicht gefunden";
        }
        if (user.getSecret() == null) {
            return "Keine Verifizierungsdaten vorhanden";
        }
        if (Boolean.TRUE.equals(user.getSecret().getIsVerifyed())) {
            return "User ist bereits verifiziert";
        }
        
        Long timestamp = user.getSecret().getVerificationTimestamp();
        if (timestamp != null) {
            long elapsed = tools.Time.currentTimeInMillis() - timestamp;
            if (elapsed > 5 * 60 * 1000) {
                return "Der Code ist abgelaufen";
            }
        }

        if (user.getSecret().getVerificationId() == null || !user.getSecret().getVerificationId().equals(verificationId)) {
            return "ID stimmt nicht";
        }
        
        user.getSecret().setIsVerifyed(true);

        try {
            em.merge(user);
        } catch (Exception e) {
            Telemetry.error("Account verification failed", e);
            return "Fehler beim update";
        }
        return "Erfolgreich validiert";
    }

    /** Stores a new hashed reset token for the account with this e-mail; returns the raw token or null. */
    @jakarta.transaction.Transactional
    public String createResetToken(String email) {
        java.util.List<model.User> users = em.createQuery("SELECT u FROM User u WHERE u.email = :email", model.User.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList();
        if (users.isEmpty() || users.get(0).getSecret() == null) {
            return null;
        }
        String token = java.util.UUID.randomUUID().toString();
        Secret secret = users.get(0).getSecret();
        secret.setResetToken(hashResetToken(token));
        secret.setResetTokenTimestamp(tools.Time.currentTimeInMillis());
        return token;
    }

    public Secret findByResetToken(String token) {
        try {
            return em.createQuery("SELECT s FROM Secret s WHERE s.resetToken = :val", Secret.class)
                    .setParameter("val", hashResetToken(token))
                    .getSingleResult();
        } catch (Exception e) {
            Telemetry.event("password_reset.token_not_found");
            return null;
        }
    }

    public static String hashResetToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
