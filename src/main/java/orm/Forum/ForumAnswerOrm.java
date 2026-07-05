package orm.Forum;

//Datentypen
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;
//
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

//Time
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import model.Forum.ForumAnswer;
import model.Forum.ForumPost;
import model.User;
import tools.Time;
import tools.HtmlSanitizer;

@ApplicationScoped
public class ForumAnswerOrm {
    private static final Logger log = Logger.getLogger(ForumAnswerOrm.class.getName());
    @Inject
    EntityManager em;

    @Inject
    HtmlSanitizer htmlSanitizer;


    public List<ForumAnswer>getAllAnswers(){
        log.info("ForumOrm/getAnswers");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa", ForumAnswer.class);
        return query.getResultList();
    }
    public List<ForumAnswer>getAnswersByUser(Long userId){
        log.info("ForumOrm/getAnswersByUser");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE fa.creator.id = :val", ForumAnswer.class);
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
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE fa.editor.id = :val", ForumAnswer.class);
        query.setParameter("val",userId);
        return query.getResultList();
   }
    public List<ForumAnswer>getAnswersByPost(Long postId){
        log.info("ForumOrm/getAnswersByPost");
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE fa.post.id = :val", ForumAnswer.class);
        query.setParameter("val",postId);
        return query.getResultList();
    }

    @Transactional
    public Response addAnswer(ForumAnswer forumAnswer, Long postId, Long userId){
        log.info("ForumAnswerOrm/addAnswer");
        ForumPost forumPost = em.find(ForumPost.class, postId);
        if(forumPost == null) return Response.status(401).entity("Post nicht gefunden").build();
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();

        if (!model.Users.Roles.hasRequiredRole(user.getRole(), forumPost.getTopic().getCategory().getVisibility())) {
            return Response.status(403).entity("Du hast keine Berechtigung, in dieser Kategorie eine Antwort zu erstellen.").build();
        }

        // Sanitize content before persisting
        forumAnswer.setContent(htmlSanitizer.sanitize(forumAnswer.getContent()));

        forumAnswer.setCreationDate(Time.currentTimeInMillis());
        forumAnswer.setPost(forumPost);
        forumAnswer.setCreator(user);
        forumAnswer.setDislikes(0L);
        forumAnswer.setLikes(0L);
        forumPost.incAnswerCount();
        try {
            em.persist(forumAnswer);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(401).entity("Fehler beim erstellen der Antwort").build();
        }

        return Response.status(201).entity("Antwort erfolgreich erstellt").build();

    }
    @Transactional
    public String updateAnswer(ForumAnswer forumAnswer, Long userId){
        log.info("ForumAnswerOrm/updateAnswer");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumAnswer forumAnswerAusDB = em.find(ForumAnswer.class, forumAnswer.getId());
        if(forumAnswerAusDB == null) return "Antwort nicht in der DB gefunden";

        User creator = forumAnswerAusDB.getCreatorObj();
        if (creator == null && !user.getRole().equals("Admin")) return "Nur Admins dürfen verwaiste Einträge bearbeiten";

        if (creator != null && !creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        // Sanitize content before merging
        forumAnswerAusDB.setContent(htmlSanitizer.sanitize(forumAnswer.getContent()));
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
        if (creator == null && !user.getRole().equals("Admin")) return "Nur Admins dürfen verwaiste Einträge bearbeiten";

        if (creator != null && !creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        try {
            em.remove(forumAnswerAusDB);
            if (forumAnswerAusDB.getPost() != null) {
                forumAnswerAusDB.getPost().decAnswerCount();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antwort";
        }

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
            em.createQuery("DELETE FROM ForumAnswer fa WHERE fa.creator.id = :val").setParameter("val", userId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antworten";
        }

        return "Antworten erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllAnswersFromTopic(Long postId){
        log.info("ForumAnswerOrm/deleteAllAnswersFromTopic");

        try {
            em.createQuery("DELETE FROM ForumPicture fp WHERE fp.answer.id IN (SELECT fa.id FROM ForumAnswer fa WHERE fa.post.id = :postId)").setParameter("postId", postId).executeUpdate();
            em.createQuery("DELETE FROM ForumAnswer fa WHERE fa.post.id = :val").setParameter("val", postId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antworten";
        }

        return "Antworten erfolgreich gelöscht:";
    }
}
