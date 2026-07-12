package orm.Secrets;

import model.Users.Secret;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

import model.User;
import orm.UserOrm;
//Logging
import java.util.logging.Logger;
import java.util.logging.Level;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@ApplicationScoped
public class SecretOrm {
    private static final Logger log = Logger.getLogger(SecretOrm.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    UserOrm userOrm;

    @Inject
    EntityManager em;

    public String addSecret(Long userId, Boolean isVerifyed, String verificationId, String password){
        log.info("SecretOrm/addSecret");

        User user = em.find(User.class, userId);

        if(user == null){
            log.warning("USER not found");
            return "User nicht gefunden";
        }

        Secret secret = new Secret(isVerifyed,verificationId);
        secret.setPassword(password);
        secret.setUser(user);
        secret.setVerificationTimestamp(tools.Time.currentTimeInMillis());

        try {
            em.persist(secret);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
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
    public String verifyUser(String email, String verificationId)
    {
        log.info("SecretOrm/verifyUser for email: " + email);

        User user = userOrm.findByEmail(email);
        if (user == null) {
            log.info("User not found: " + email);
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
            log.log(Level.SEVERE, "Result: " + e.getMessage(), e);
            return "Fehler beim update";
        }
        return "Erfolgreich validiert";
    }

    public Secret findByResetToken(String token) {
        try {
            return em.createQuery("SELECT s FROM Secret s WHERE s.resetToken = :val", Secret.class)
                    .setParameter("val", hashResetToken(token))
                    .getSingleResult();
        } catch (Exception e) {
            log.log(Level.FINE, "Reset token was not found", e);
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
