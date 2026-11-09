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

    @WithSpan
    public String generateVerificationId()
    {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    public static final String VERIFIED = "Erfolgreich validiert";
    /** One answer for every failure, so the endpoint does not reveal which addresses are registered. */
    public static final String VERIFICATION_FAILED =
            "Der Code ist ungültig oder abgelaufen. Fordere bei Bedarf einen neuen Code an.";
    private static final long VERIFICATION_TTL_MS = 5 * 60 * 1000L;

    @Transactional
    @WithSpan
    public String verifyUser(String email, String verificationId)
    {
        User user = email != null ? userOrm.findByEmail(email.trim()) : null;
        Secret secret = user != null ? user.getSecret() : null;
        if (secret == null || secret.getVerificationId() == null) {
            Telemetry.event("verification.rejected", "verification.reason", "no_code");
            return VERIFICATION_FAILED;
        }
        boolean matches = verificationId != null && MessageDigest.isEqual(
                secret.getVerificationId().getBytes(StandardCharsets.UTF_8),
                verificationId.trim().getBytes(StandardCharsets.UTF_8));

        // A repeated submit of the right code after success is not an error.
        if (Boolean.TRUE.equals(secret.getIsVerifyed())) {
            return matches ? VERIFIED : VERIFICATION_FAILED;
        }

        Long timestamp = secret.getVerificationTimestamp();
        boolean expired = timestamp != null && tools.Time.currentTimeInMillis() - timestamp > VERIFICATION_TTL_MS;
        if (expired || secret.getVerificationAttempts() >= Secret.MAX_CODE_ATTEMPTS) {
            Telemetry.event("verification.rejected", "verification.reason", expired ? "expired" : "attempts_exhausted");
            return VERIFICATION_FAILED;
        }

        if (!matches) {
            int attempts = secret.getVerificationAttempts() + 1;
            secret.setVerificationAttempts(attempts);
            if (attempts >= Secret.MAX_CODE_ATTEMPTS) {
                // Five wrong guesses void the code; a new one has to be requested.
                secret.setVerificationId(null);
                Telemetry.warn("Verification code entered wrongly too often", "user.id", user.getId(),
                        "security.event", "verification_code_exhausted");
            }
            return VERIFICATION_FAILED;
        }

        secret.setIsVerifyed(true);
        secret.setVerificationAttempts(0);
        return VERIFIED;
    }

    /** Stores a new hashed reset token for the account with this e-mail; returns the raw token or null. */
    @jakarta.transaction.Transactional
    @WithSpan
    public String createResetToken(String email) {
        java.util.List<model.User> users = em.createQuery("SELECT u FROM User u WHERE u.email = :email", model.User.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList();
        if (users.isEmpty() || users.get(0).getSecret() == null) {
            return null;
        }
        // Authentik owns linked accounts; a local password would outlive a block or group change there.
        if (users.get(0).isOidcLinked()) {
            Telemetry.event("password_reset.oidc_account_skipped", "user.id", users.get(0).getId());
            return null;
        }
        String token = java.util.UUID.randomUUID().toString();
        Secret secret = users.get(0).getSecret();
        secret.setResetToken(hashResetToken(token));
        secret.setResetTokenTimestamp(tools.Time.currentTimeInMillis());
        return token;
    }

    @WithSpan
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
