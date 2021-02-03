package orm;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import model.*;

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
            return ""+e;
        }
     
        // Id zurückgeben
        return "" + getUserByUsername(usr.getUserName()).get(0).getId();
    }

}
