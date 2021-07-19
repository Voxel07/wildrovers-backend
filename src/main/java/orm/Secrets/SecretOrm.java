package orm.Secrets;

import java.util.UUID;
import model.Users.Secret;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.inject.Inject;

import model.User;
import model.Users.Secret;
//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class SecretOrm {
    private static final Logger log = Logger.getLogger(SecretOrm.class.getName());

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
    
    public String generateVerificationId(){

        return UUID.randomUUID().toString();
    }
}
