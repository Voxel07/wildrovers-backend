package orm.Forum;

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

import java.util.logging.Level;
//Logging
import java.util.logging.Logger;

//Time
import tools.Time;
import tools.HtmlSanitizer;

import model.User;
import model.Forum.ForumCategory;
import model.Forum.ForumTopic;

@ApplicationScoped
public class ForumTopicOrm {

    private static final Logger log = Logger.getLogger(ForumTopicOrm.class.getName());
    @Inject
    EntityManager em;

    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    HtmlSanitizer htmlSanitizer;



    //Basic GET methods
    public List<ForumTopic>getAllTopics()
    {
        log.info("ForumTopicOrm/getTopics");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category", ForumTopic.class);
        return query.getResultList();
    }

    @Transactional
    public List<ForumTopic>getTopicById(Long topicId)
    {
        log.info("ForumTopicOrm/getTopicById");
        ForumTopic topic = em.find(ForumTopic.class, topicId);
        if (topic != null) {
            topic.setViews((topic.getViews() != null ? topic.getViews() : 0L) + 1);
            em.merge(topic);
        }
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category WHERE ft.id =: val", ForumTopic.class);
        query.setParameter("val", topicId);
        return query.getResultList();
    }

    public List<ForumTopic>getTopicsByUser(Long userId)
    {
        log.info("ForumTopicOrm/getTopicByUser");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category WHERE ft.creator.id = :val", ForumTopic.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    public List<ForumTopic>getTopicsByCategory(Long categoryId)
    {
        log.info("ForumTopicOrm/getTopicByCategory");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category WHERE ft.category.id = :val", ForumTopic.class);
        query.setParameter("val", categoryId);
        return query.getResultList();
    }

    public List<ForumTopic>getTopicsByTopic(String topic)
    {
        log.info("ForumTopicOrm/getTopicsByTopic");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft WHERE topic =: val", ForumTopic.class);
        query.setParameter("val", topic);
        return query.getResultList();
    }

    //CRUD operations for Forum Categories
    /*
        Brief addTopic
        - Checks if the Topic already exists
        - Check if passed Category exists
        - Create a formatted Time string from the local Server time
        - returns success or the error that accrued as a string
    */
    @Transactional
    public Response addTopic(ForumTopic topic, Long categoryId, Long userId){
        log.info("ForumTopicOrm/addTopic");
        // Sanitize topic name (plain text — no HTML allowed in topic titles)
        topic.setTopic(htmlSanitizer.sanitizeTitle(topic.getTopic()));
        //Check if Topic exists
        if(!getTopicsByTopic(topic.getTopic()).isEmpty()){
            log.warning("duplicate topic");
            return Response.status(401).entity("Thema exestiert bereits").build();
        }
        //Check if Category exists
        List<ForumCategory> forumCategorys = forumCategoryOrm.getCategoriesById(categoryId);
        if(forumCategorys.isEmpty()) return Response.status(401).entity("Kategorie nicht gefunden").build();
        ForumCategory forumCategory = forumCategorys.get(0);

        forumCategory.incTopicCount();
        topic.setCategory(forumCategory);
        //setCreationDate
        topic.setCreationDate(Time.currentTimeInMillis());
        //get corresponding user from db. Exit if not found
        User u = em.find(User.class, userId);
        if(u == null){
            log.warning("User nicht in der DB gefunden");
            return Response.status(401).entity("User nicht gefunden").build();
        }

        if (!model.Users.Roles.hasRequiredRole(u.getRole(), forumCategory.getVisibility())) {
            return Response.status(403).entity("Du hast keine Berechtigung, in dieser Kategorie ein Thema zu erstellen.").build();
        }

        u.getActivityForum().incTopicCount();
        topic.setCreator(u);
        topic.setPostCount(0L);
        topic.setViews(0L);

        try {
            em.persist(topic);
        } catch (Exception e) {
             log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(500).entity("Fehler beim erstellen des neuen Tehmas").build();
        }
        return Response.status(201).entity(topic).build();
    }
    /**
     * Updates the topic name. Only the creator or an Admin may update a topic.
     */
    @Transactional
    public String updateTopic(ForumTopic forumTopic, Long userId){
        log.info("ForumTopicOrm/updateTopic");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumTopic forumTopicAusDB = em.find(ForumTopic.class, forumTopic.getId());
        if(forumTopicAusDB == null) return "Thema nicht in der DB gefunden";

        User creator = forumTopicAusDB.getCreatorObj();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        // Sanitize topic name before merging
        forumTopicAusDB.setTopic(htmlSanitizer.sanitizeTitle(forumTopic.getTopic()));

        try{
            em.merge(forumTopicAusDB);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Antwort";
        }
        return "Thema erfolgreich aktualisert";
    }
    /**
     * Deletes a topic and all its nested posts/answers. Only the creator or an Admin may delete a topic.
     */
    @Transactional
    public String deleteTopic(ForumTopic forumTopic, Long userId){
        log.info("ForumTopicOrm/deleteTopic");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";
        Long topicId = forumTopic.getId();
        ForumTopic forumTopicAusDB = em.find(ForumTopic.class, topicId);
        if(forumTopicAusDB == null) return "Thema nicht in der DB gefunden";

        User creator = forumTopicAusDB.getCreatorObj();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        try {
            forumPostOrm.deleteAllPostsFromTopic(topicId);

            creator.getActivityForum().decTopicCount();
            if (forumTopicAusDB.getCategory() != null) {
                forumTopicAusDB.getCategory().decTopicCount();
            }

            em.remove(forumTopicAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen des Topics";
        }

        return "Thema erfolgreich gelöscht";
    }
    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllTopicsFromUser(Long userId){
        log.info("ForumAnswerOrm/deleteAllTopicsFromUser");

        try {
            em.createQuery("DELETE FROM ForumTopic ft WHERE ft.creator.id = :val").setParameter("val", userId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Themen";
        }


        return "Themen erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllTopicsFromCategory(Long categoryId){
        log.info("ForumCategoryOrm/deleteAllTopicsFromCategory");

        //Get all answers that will be affected to Update the affected user.
        List<ForumTopic> allTopics = getTopicsByCategory(categoryId);
        HashMap<User, Long> map = new HashMap<User,Long>();
        //Loop all answers to count the number of deleted answers per user.
        for (ForumTopic forumTopic : allTopics) {
           User u = forumTopic.getCreatorObj();
           if(map.containsKey(u))
           {
                map.put(u, map.get(u) + 1);
           }
           else{
               map.put(u, 1L);
           }
           forumPostOrm.deleteAllPostsFromTopic(forumTopic.getId());
        }
        try {
            em.createQuery("DELETE FROM ForumTopic ft WHERE ft.category.id = :val").setParameter("val", categoryId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antworten";
        }

        for (Entry<User, Long> entry : map.entrySet()) {
            User k = entry.getKey();
            Long v = entry.getValue();
            k.getActivityForum().setTopicCount(k.getActivityForum().getTopicCount() - v);
        }
        return "Topics erfolgreich gelöscht:";
    }


}
