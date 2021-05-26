package orm.Forum;
//Datentypen
import java.util.List;
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
        System.out.println("vor if");
        if(user == null) return "User nicht gefunden";

        System.out.println("vor getCreator");
        ForumAnswer forumAnswerAusDB = em.find(ForumAnswer.class, forumAnswer.getId());
        if(forumAnswerAusDB == null) return "Antwort nicht in der DB gefunden";

        System.out.println(forumAnswerAusDB.toString());
        User creator = forumAnswer.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        System.out.println("vor if2");
        // if(!creatorId.equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";
        
        System.out.println("vor datetime");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        forumAnswer.setCreationDate(dtf.format(now));
        forumAnswer.setEditor(user);

        try{
            em.merge(forumAnswer);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Antwort";
        }
        return "Antwort erfolgreich aktualisert";

    }
    @Transactional
    public String deleteAnswer(){
        return "TODO:";
    }
}
