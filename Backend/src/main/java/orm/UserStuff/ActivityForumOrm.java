package orm.UserStuff;

import javax.enterprise.context.RequestScoped;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import model.User;
import model.Users.ActivityForum;
import resources.GenerateToken;
import java.time.LocalDate;
//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
//@RequestScoped
public class ActivityForumOrm {
    private static final Logger log = Logger.getLogger(ActivityForumOrm.class.getName());
    @Inject
    EntityManager em;

    // public ActivityForum getUserActivityForum(Long userId){
    //     log.info("ActivityForumOrm/getUserActivityForum");
    //     User tmp = em.find(User.class, userId);
    //     return tmp.getActivityForum();
    //     // log.info("ActivityForumOrm/getCategoryCount");
    //     // TypedQuery<ActivityForum> query = em.createQuery("SELECT af FORM ActivityForum af WHERE user_id =: val",ActivityForum.class);
    //     // query.setParameter("val", userId);
    //     // return query.getSingleResult();
    // }

    public String addActivityForum(Long userId){
        log.info("ActivityForumOrm/addActivityForum");

        User user = em.find(User.class, userId);
        if(user == null){
            log.warning("USER not found");
            return "User nicht gefunden";
        }
        ActivityForum activityForum = new ActivityForum();
        activityForum.setUser(user);

        try {
            em.persist(activityForum);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim erstellen des Aktivität";
        }
        return "Forum Aktivitäten erfolgreich erstellt";
    }
    public String removeActivityForum(Long userId){
        log.info("ActivityForumOrm/removeActivityForum");

        User user = em.find(User.class, userId);
        if(user == null){
            log.warning("USER not found");
            return "User nicht gefunden";
        }
        ActivityForum activityForum = user.getActivityForum();
        try {
            em.remove(activityForum);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim löschen des Aktivität";
        }
        return "Forum Aktivitäten erfolgreich gelöscht";
    }
}
