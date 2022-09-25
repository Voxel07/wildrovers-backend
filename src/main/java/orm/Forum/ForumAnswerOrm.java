package orm.Forum;

//Datentypen
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;
//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

//Time
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import model.Forum.ForumAnswer;
import model.Forum.ForumPost;
import model.User;

@ApplicationScoped
public class ForumAnswerOrm {
    private static final Logger log = Logger.getLogger(ForumAnswerOrm.class.getName());
    @Inject
    EntityManager em;
    @Inject
    ForumAnswerOrm forumAnswerOrm;

    public List<ForumAnswer>getAllAnswers(){
        log.info("ForumOrm/getAnswers");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa", ForumAnswer.class);
        return query.getResultList();
    }
    public List<ForumAnswer>getAnswersByUser(Long userId){
        log.info("ForumOrm/getAnswersByUser");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE user_id =: val", ForumAnswer.class);
        query.setParameter("val",userId);
        return query.getResultList();
    }
    public List<ForumAnswer>getAnswersById(Long answerId){
        log.info("ForumOrm/getAnswersById");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE id =: val", ForumAnswer.class);
        query.setParameter("val",answerId);
        return query.getResultList();
   }
    public List<ForumAnswer>getAnswersByEditor(Long userId){
        log.info("ForumOrm/getAnswersByEditor");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE  editor_id =: val", ForumAnswer.class);
        query.setParameter("val",userId);
        return query.getResultList();
   }
    public List<ForumAnswer>getAnswersByPost(Long postId){
        log.info("ForumOrm/getAnswersByPost");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE post_id =: val", ForumAnswer.class);
        query.setParameter("val",postId);
        return query.getResultList();
    }

    @Transactional
    public String addAnswer(ForumAnswer forumAnswer, Long postId, Long userId){
        log.info("ForumAnswerOrm/addAnswer");
        ForumPost forumPost = em.find(ForumPost.class, postId);
        if(forumPost == null) return "Post nicht gefunden";
        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        forumAnswer.setCreationDate(dtf.format(now));
        forumAnswer.setPost(forumPost);
        forumAnswer.setCreator(user);
        forumAnswer.setDislikes(0L);
        forumAnswer.setLikes(0L);
        forumPost.incAnswerCount();
        user.getActivityForum().incAnswerCount();
        try {
            em.persist(forumAnswer);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim erstellen der Antwort";
        }

        return "Antwort erfolgreich erstellt";

    }
    @Transactional
    public String updateAnswer(ForumAnswer forumAnswer, Long userId){
        log.info("ForumAnswerOrm/updateAnswer");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumAnswer forumAnswerAusDB = em.find(ForumAnswer.class, forumAnswer.getId());
        if(forumAnswerAusDB == null) return "Antwort nicht in der DB gefunden";

        User creator = forumAnswerAusDB.getCreatorObj();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        forumAnswerAusDB.setContent(forumAnswer.getContent());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        forumAnswerAusDB.setEditDate(dtf.format(now));
        forumAnswerAusDB.setEditor(user);

        try{
            em.merge(forumAnswerAusDB);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Antwort";
        }
        return "Antwort erfolgreich aktualisert";

    }
    @Transactional
    public String deleteAnswer(ForumAnswer forumAnswer, Long userId){
        log.info("ForumAnswerOrm/deleteAnswer");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumAnswer forumAnswerAusDB = em.find(ForumAnswer.class, forumAnswer.getId());
        if(forumAnswerAusDB == null) return "Antwort nicht in der DB gefunden";

        User creator = forumAnswerAusDB.getCreatorObj();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        try {
            em.remove(forumAnswerAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antwort";
        }

        creator.getActivityForum().decAnswerCount();
        forumAnswerAusDB.getPost().decAnswerCount();

        return "Antwort erfolgreich gelöscht";
    }
    /**
     * No checks, because this function does not have a public endpoint
     * Gets called when a User is Deleted.
     */

    @Transactional
    public String deleteAllAnswersFromUser(Long userId){
        log.info("ForumAnswerOrm/deleteAllAnswersFromUser");

        try {
            em.createQuery("DELETE fa FROM ForumAnswer fa WHERE user_id =: val").setParameter("val", userId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antworten";
        }

        //Maybe set count to 0
        // User user = em.find(User.class, userId);
        // user.getActivityForum().setAnswerCount(0L);
        return "Antworten erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllAnswersFromTopic(Long postId){
        log.info("ForumAnswerOrm/deleteAllAnswersFromTopic");

        //Get all answers that will be affected to Update the affected user.
        List<ForumAnswer> allAnswers = getAnswersByPost(postId);
        HashMap<User, Long> map = new HashMap<User,Long>();
        //Loop all answers to count the number of deleted answers per user.
        for (ForumAnswer forumAnswer : allAnswers) {
           User u = forumAnswer.getCreatorObj();
           if(map.containsKey(u))
           {
                map.put(u, map.get(u) + 1);
           }
           else{
               map.put(u, 1L);
           }
        }
        try {
            em.createQuery("DELETE FROM ForumAnswer WHERE post_id =: val").setParameter("val", postId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antworten";
        }

        for (Entry<User, Long> entry : map.entrySet()) {
            User k = entry.getKey();
            Long v = entry.getValue();
            k.getActivityForum().setAnswerCount(k.getActivityForum().getAnswerCount() - v);
        }
        return "Antworten erfolgreich gelöscht:";
    }
}
