package orm.UserStuff;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import model.User;
import model.Users.ActivityForum;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

@ApplicationScoped
//@RequestScoped
public class ActivityForumOrm {
    private static final Logger log = Logger.getLogger(ActivityForumOrm.class.getName());
  
    @Inject
    EntityManager em;


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
