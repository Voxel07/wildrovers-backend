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

//Time

import model.Forum.ForumAnswer;
import model.Forum.ForumPost;
import model.User;
import tools.Time;
import tools.HtmlSanitizer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;

@ApplicationScoped
public class ForumAnswerOrm {
    @Inject
    EntityManager em;

    @Inject
    HtmlSanitizer htmlSanitizer;

    @Inject
    tools.ForumImageStore imageStore;


    /**
     * Paged answer list for one reader, oldest first. Only one filter is applied, in the order
     * answer, post, user, editor.
     */
    @WithSpan
    public ForumPage<ForumAnswer> findAnswers(Long answerId, Long postId, Long userId, Long editorId,
            List<String> visibleRoles, Integer page, Integer size) {
        String filter = "";
        Object value = null;
        if (answerId != null) { filter = " AND fa.id = :val"; value = answerId; }
        else if (postId != null) { filter = " AND p.id = :val"; value = postId; }
        else if (userId != null) { filter = " AND fa.creator.id = :val"; value = userId; }
        else if (editorId != null) { filter = " AND fa.editor.id = :val"; value = editorId; }
        String from = " FROM ForumAnswer fa LEFT JOIN fa.post p LEFT JOIN p.topic t LEFT JOIN t.category c";
        String where = " WHERE " + ForumPage.visibilityClause("c", visibleRoles) + filter;

        TypedQuery<ForumAnswer> query = em.createQuery(
                "SELECT fa" + from + where + " ORDER BY fa.creationDate ASC, fa.id ASC", ForumAnswer.class);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(fa)" + from + where, Long.class);
        ForumPage.bindVisibility(query, visibleRoles);
        ForumPage.bindVisibility(count, visibleRoles);
        if (value != null) {
            query.setParameter("val", value);
            count.setParameter("val", value);
        }
        query.setFirstResult(ForumPage.firstResult(page, size));
        query.setMaxResults(ForumPage.pageSize(size));
        return new ForumPage<>(query.getResultList(), count.getSingleResult());
    }

    @WithSpan
    public List<ForumAnswer>getAllAnswers(){
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa", ForumAnswer.class);
        return query.getResultList();
    }
    @WithSpan
    public List<ForumAnswer>getAnswersByUser(Long userId){
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE fa.creator.id = :val", ForumAnswer.class);
        query.setParameter("val",userId);
        return query.getResultList();
    }
    @WithSpan
    public List<ForumAnswer>getAnswersById(Long answerId){
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE id =: val", ForumAnswer.class);
        query.setParameter("val",answerId);
        return query.getResultList();
   }
    @WithSpan
    public List<ForumAnswer>getAnswersByEditor(Long userId){
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE fa.editor.id = :val", ForumAnswer.class);
        query.setParameter("val",userId);
        return query.getResultList();
   }
    @WithSpan
    public List<ForumAnswer>getAnswersByPost(Long postId){
        TypedQuery<ForumAnswer> query = em.createQuery("SELECT fa FROM ForumAnswer fa WHERE fa.post.id = :val ORDER BY fa.creationDate ASC, fa.id ASC", ForumAnswer.class);
        query.setParameter("val",postId);
        return query.getResultList();
    }

    @Transactional
    @WithSpan
    public Response addAnswer(ForumAnswer forumAnswer, Long postId, Long userId){
        ForumPost forumPost = em.find(ForumPost.class, postId);
        if(forumPost == null) return Response.status(404).entity("Post nicht gefunden").build();
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();

        if (!model.Users.Roles.hasRequiredRole(user.getRole(), forumPost.getTopic().getCategory().getVisibility())) {
            return Response.status(403).entity("Du hast keine Berechtigung, in dieser Kategorie eine Antwort zu erstellen.").build();
        }

        // Sanitize content and attach the author's editor uploads to the parent post
        forumAnswer.setContent(imageStore.prepareContent(forumAnswer.getContent(), forumPost.getId(), userId));

        forumAnswer.setCreationDate(Time.currentTimeInMillis());
        forumAnswer.setPost(forumPost);
        forumAnswer.setCreator(user);
        forumAnswer.setDislikes(0L);
        forumAnswer.setLikes(0L);
        try {
            em.persist(forumAnswer);
        } catch (Exception e) {
            Telemetry.error("Adding answer failed", e, "forum.post.id", postId, "user.id", userId);
            return Response.status(500).entity("Fehler beim erstellen der Antwort").build();
        }
        ForumCounters.adjust(em, "ForumPost", "answerCount", forumPost.getId(), 1);

        return Response.status(201).entity("Antwort erfolgreich erstellt").build();

    }
    @Transactional
    @WithSpan
    public Response updateAnswer(ForumAnswer forumAnswer, Long userId){
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();

        ForumAnswer forumAnswerAusDB = em.find(ForumAnswer.class, forumAnswer.getId());
        if(forumAnswerAusDB == null) return Response.status(404).entity("Antwort nicht in der DB gefunden").build();

        User creator = forumAnswerAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        // Sanitize content and attach the editor's uploads to the parent post
        if (forumAnswer.getContent() != null) {
            Long postId = forumAnswerAusDB.getPost() != null ? forumAnswerAusDB.getPost().getId() : null;
            forumAnswerAusDB.setContent(imageStore.prepareContent(forumAnswer.getContent(), postId, userId));
        }
        forumAnswerAusDB.setEditDate(Time.currentTimeInMillis());
        forumAnswerAusDB.setEditor(user);

        try{
            em.merge(forumAnswerAusDB);
        }catch(Exception e){
            Telemetry.error("Updating answer failed", e, "forum.answer.id", forumAnswer.getId(), "user.id", userId);
            return Response.status(500).entity("Fehler beim updaten der Antwort").build();
        }
        return Response.status(200).entity("Antwort erfolgreich aktualisert").build();

    }
    @Transactional
    @WithSpan
    public Response deleteAnswer(ForumAnswer forumAnswer, Long userId){
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();

        ForumAnswer forumAnswerAusDB = em.find(ForumAnswer.class, forumAnswer.getId());
        if(forumAnswerAusDB == null) return Response.status(404).entity("Antwort nicht in der DB gefunden").build();

        User creator = forumAnswerAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        try {
            em.remove(forumAnswerAusDB);
            if (forumAnswerAusDB.getPost() != null) {
                ForumCounters.adjust(em, "ForumPost", "answerCount", forumAnswerAusDB.getPost().getId(), -1);
            }
        } catch (Exception e) {
            Telemetry.error("Deleting answer failed", e, "forum.answer.id", forumAnswer.getId(), "user.id", userId);
            return Response.status(500).entity("Fehler beim Löschen der Antwort").build();
        }

        return Response.status(200).entity("Antwort erfolgreich gelöscht").build();
    }
    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    @WithSpan
    public String deleteAllAnswersFromTopic(Long postId){
        try {
            em.createQuery("DELETE FROM ForumAnswer fa WHERE fa.post.id = :val").setParameter("val", postId).executeUpdate();
        } catch (Exception e) {
            Telemetry.error("Deleting answers of post failed", e, "forum.post.id", postId);
            return "Fehler beim Löschen der Antworten";
        }

        return "Antworten erfolgreich gelöscht:";
    }
}
