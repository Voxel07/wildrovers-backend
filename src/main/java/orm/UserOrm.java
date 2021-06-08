package orm;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import io.quarkus.elytron.security.common.BcryptUtil;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;


import model.User;
import orm.UserStuff.ActivityForumOrm;

import java.time.LocalDate;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
public class UserOrm {
    private static final Logger log = Logger.getLogger(UserOrm.class.getName());
    @Inject
    EntityManager em;

    @Inject
    ActivityForumOrm activityForumOrm;


    public List<User> getUsers() {
         log.info("UserOrm/getUsers");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    public List<User> getUserById(Long userId) {
         log.info("UserOrm/getUserById");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE id =: val", User.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    public List<User> getUserByUsername(String userName) {
         log.info("UserOrm/getUserByUsername");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE userName =: val", User.class);
        query.setParameter("val", userName);
        return query.getResultList();
    }

    @Transactional
    public String addUser(User usr) {
         log.info("UserOrm/addUser");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2", User.class);
        query.setParameter("val1", usr.getUserName());
        query.setParameter("val2", usr.getEmail());
        if (!query.getResultList().isEmpty()) {
            return "Nutzer bereits bekannt";
        }
 
        usr.setPassword(BcryptUtil.bcryptHash(usr.getPassword()));
        usr.setRegDate(LocalDate.now());
        usr.setActive(true);
       

        // Nutzer einfügen
        try {
            em.persist(usr);
        } catch (Exception e) {
            return "Fehler beim Nutzer einfügen" + e;
        }
        //NOTE:
        /**
         * Needs to be after the User has been persisted to the Database so we can get the ID;
         */
        //create Activity logs
        activityForumOrm.addActivityForum(usr.getId());
        // Id zurückgeben
        return "" + getUserByUsername(usr.getUserName()).get(0).getId();
    }

    @Transactional
    public String updateUser(User u) {
         log.info("UserOrm/updateUser");

        boolean error = false;
        String errorMSG = "";
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2 OR u.id =: val3",
                User.class);
        query.setParameter("val1", u.getUserName());
        query.setParameter("val2", u.getEmail());
        query.setParameter("val3", u.getId());

        List<User> userAusDB = query.getResultList();
        // Wenn user zurückgekomen sind
        if (userAusDB.isEmpty()) return "Keinen Nutzer mit diesen Daten gefunden";
            // Alle user durchlaufen
        for (User aktUser : userAusDB) {
            // Überprüfen ob die ID die gleiche ist.
            if (!aktUser.getId().equals(u.getId()) && !error) {
                // Wenn nicht prüfen ob der Name doppelt ist
                // if (aktUser.getEmail().equals(u.getEmail()) && aktUser.getUserName().equals(u.getUserName())) {
                //     error = true;
                //     errorMSG = "Email und UserName bereits vergeben";
                // } 
                if (aktUser.getUserName().equals(u.getUserName())) {
                    error = true;
                    errorMSG = "Username bereits vergeben";
                }
                // Oder die Email
                else if (aktUser.getEmail().equals(u.getEmail())) {
                    error = true;
                    errorMSG = "Email bereits vergeben";
                }
            }
        }
        
        if (!error) {
            try {
                em.merge(u);
                errorMSG = "User erfolgreich aktualisiert";
            } catch (Exception e) {
                errorMSG = "Fehler beim Updaten des User";
            }
        }
        return errorMSG;
    }

    
    public Boolean loginUser(User usr){
        log.info("UserOrm/loginUser");
        log.info(usr.toString());
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2", User.class);
        query.setParameter("val1", usr.getUserName());
        query.setParameter("val2", usr.getEmail());
        // Falls kein User mit dem namen gefunden wurde
        User u = query.getSingleResult();
        if (u == null) {
            log.info("Kein nutzer mit den daten gefunden");
            return false;
        }
        try {
           return verifyBCryptPassword(u.getPassword(), usr.getPassword());
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return false;
        }
    }

    public static boolean verifyBCryptPassword(String bCryptPasswordHash, String passwordToVerify) throws Exception {

        WildFlyElytronPasswordProvider provider = new WildFlyElytronPasswordProvider();

        // 1. Create a BCrypt Password Factory
        PasswordFactory passwordFactory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT, provider);

        // 2. Decode the hashed user password
        Password userPasswordDecoded = ModularCrypt.decode(bCryptPasswordHash);

        // 3. Translate the decoded user password object to one which is consumable by this factory.
        Password userPasswordRestored = passwordFactory.translate(userPasswordDecoded);

        // Verify existing user password you want to verify
        return passwordFactory.verify(userPasswordRestored, passwordToVerify.toCharArray());

    }
    

}
