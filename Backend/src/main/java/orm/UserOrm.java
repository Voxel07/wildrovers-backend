package orm;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import model.User;

@ApplicationScoped
public class UserOrm {
    @Inject
    EntityManager em;

    public List<User> getUsers() {
        System.out.println("UserOrm/getUsers");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    public List<User> getUserById(Long userId) {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE id =: val", User.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    public List<User> getUserByUsername(String userName) {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE username =: val", User.class);
        query.setParameter("val", userName);
        return query.getResultList();
    }

    @Transactional
    public String addUser(User usr) {
        // Prüfen dass Username und Email einzigartig sind
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.username =: val1 OR u.email =: val2",
                User.class);
        query.setParameter("val1", usr.getUserName());
        query.setParameter("val2", usr.getEmail());
        if (!query.getResultList().isEmpty()) {
            return "Nutzer bereits bekannt";
        }

        // Nutzer einfügen
        try {
            em.persist(usr);
        } catch (Exception e) {
            return "" + e;
        }

        // Id zurückgeben
        return "" + getUserByUsername(usr.getUserName()).get(0).getId();
    }

    @Transactional
    public String updateUser(User u) {
        boolean error = false;
        String errorMSG = "";
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.username =: val1 OR u.email =: val2",
                User.class);
        query.setParameter("val1", u.getUserName());
        query.setParameter("val2", u.getEmail());

        List<User> userAusDB = query.getResultList();
        //Wenn user zurückgekomen sind
        if (!userAusDB.isEmpty()) {
            //Alle user durchlaufen 
            for (User aktUser : userAusDB) {
                //Ürüfen ob die ID die gleiche ist. 
                if (!aktUser.getId().equals(u.getId()) && !error) 
                {
                    //Wenn nicht prüfen ob der Name doppelt ist
                    if(aktUser.getEmail().equals(u.getEmail()) && aktUser.getUserName().equals(u.getUserName())){
                        error = true;
                        errorMSG = "Email und UserName bereits vergeben";
                    }
                    else if (aktUser.getUserName().equals(u.getUserName())) 
                    {
                        error = true;
                        errorMSG = "Username bereits vergeben";
                    } 
                    //Oder die Email
                    else if (aktUser.getEmail().equals(u.getEmail())) 
                    {
                        error = true;
                        errorMSG = "Email bereits vergeben";
                    }
                }
            }
        } 
        if (!error) 
        {
            try {
                em.merge(u);
                errorMSG = "User erfolgreich aktualisiert";
            } catch (Exception e) {
                errorMSG = "Fehler beim Updaten des User";
            }
        }
        return errorMSG;
    }


}
