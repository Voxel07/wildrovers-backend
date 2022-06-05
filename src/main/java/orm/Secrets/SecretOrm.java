package orm.Secrets;

import java.util.UUID;
import model.Users.Secret;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.inject.Inject;

import model.User;
import orm.UserOrm;
//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class SecretOrm {
    private static final Logger log = Logger.getLogger(SecretOrm.class.getName());

    @Inject
    UserOrm userOrm;

    @Inject
    EntityManager em;

    public String addSecret(Long userId, Boolean isVerifyed, String verificationId){
        log.info("SecretOrm/addSecret");

        User user = em.find(User.class, userId);

        if(user == null){
            log.warning("USER not found");
            return "User nicht gefunden";
        }

        Secret secret = new Secret(isVerifyed,verificationId);
        secret.setUser(user);

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
        return UUID.randomUUID().toString();
    }

    @Transactional
    public String verifyUser(Long userId, String verificationId)
    {
        log.info("SecretOrm/verifyUser");

        User user;
        try {
            user = userOrm.getUserById(userId).get(0);
        } catch (Exception e) {
            log.info("User not found");
            return"Der User wurde nich gefunden";
        }
        if (!user.getSecret().getVerificationId().equals(verificationId)){
            return "ID stimmt nicht";
        }
        if(user.getSecret().getIsVerifyed().equals(true)){
            return "User ist bereits verifiziert";
        }
        user.getSecret().setIsVerifyed(true);

        try {
            em.merge(user);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim update";
        }
        return "Erfolgreich validiert";
    }
}
