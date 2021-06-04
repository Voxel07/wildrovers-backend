package orm;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import model.User;
import orm.UserStuff.ActivityForumOrm;

import java.time.LocalDate;

//Logging
import java.util.logging.Logger;

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

        // Prüfen dass Username und Email einzigartig sind
        // if (testInputs(usr) == "default") {
        //     return "da passt was nicht";
        // } else {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2",
                User.class);
        query.setParameter("val1", usr.getUserName());
        query.setParameter("val2", usr.getEmail());
        if (!query.getResultList().isEmpty()) {
            return "Nutzer bereits bekannt";
        }

        usr.setRegDate(LocalDate.now());
       

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

    // }

    // public String testInputs(User usr) {
    //     if (!validateEmail(usr.getEmail())) {
    //         return "invalid Email";
    //     }
    //     return "default";
    // }

    // public Boolean validateEmail(String email){
    //     Pattern p = Pattern.compile("/\A[^@]+@([^@\.]+\.)+[^@\.]+\z/");
    //     Matcher m = p.matcher(email);
    //     return m.matches();
    // }

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

    @Transactional
    public Boolean loginUser(User usr) {
         log.info("UserOrm/loginUser");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName = :val OR email = :val2" , User.class);
        query.setParameter("val", usr.getUserName());
        query.setParameter("val2", usr.getEmail());
        // Falls kein User mit dem namen gefunden wurde
        if (query.getResultList().isEmpty()) {
            return false;
        }
        return (query.getSingleResult().getPassword().equals(usr.getPassword()));
       

    }

    

}
