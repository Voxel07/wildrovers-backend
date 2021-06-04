package orm.Forum;

//Additional Data Types
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import java.util.logging.Level;
//Logging
import java.util.logging.Logger;

//Time
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;

import orm.UserOrm;
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

    // @Inject
    // UserOrm userOrm;

    //Basic GET methods 
    public List<ForumTopic>getAllTopics(){
        log.info("ForumTopicOrm/getTopics");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft", ForumTopic.class);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicById(Long topicId){
        log.info("ForumTopicOrm/getTopicById");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft WHERE id =: val", ForumTopic.class);
        query.setParameter("val", topicId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByUser(Long userId){
        log.info("ForumTopicOrm/getTopicByUser");        
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft WHERE user_id =: val", ForumTopic.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByCategory(Long categoryId){
        log.info("ForumTopicOrm/getTopicByCategory");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft WHERE category_id =: val", ForumTopic.class);
        query.setParameter("val", categoryId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByTopic(String topic){
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
    public String addTopic(ForumTopic topic, Long categoryId, Long userId){
        log.info("ForumTopicOrm/addTopic");
        //Check if Topic exists
        if(!getTopicsByTopic(topic.getTopic()).isEmpty()){
            log.warning("duplicate topic");
            return "Thema exestiert bereits";
        } 
        //Check if Category exists
        List<ForumCategory> forumCategorys = forumCategoryOrm.getCategoriesById(categoryId);
        if(forumCategorys.isEmpty()) return "Kategorie nicht gefunden";
        ForumCategory forumCategory = forumCategorys.get(0);
        // if(forumCategory == null){
        //     log.warning("CATEGORY not found");
        //     return "Angegbene Kategorie nicht gefunden";
        // }
        forumCategory.incTopicCount();
        topic.setCategory(forumCategory);
        //setCreationDate        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        topic.setCreationDate(dtf.format(now));
        //get corresponding user from db. Exit if not found
        User u = em.find(User.class, userId);
        if(u == null){
            log.warning("User nicht in der DB gefunden");
            return "User nicht gefunden";
        } 

        u.getActivityForum().incTopicCount();
        topic.setCreator(u);
        topic.setPostCount(0L);

        try {
            em.persist(topic);
        } catch (Exception e) {
             log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim erstellen des neuen Tehmas";
        }
        return "Thema erfolgreich erstellt";
    }
    /*
        Brief updateTopic
        - ToDo
    */
    @Transactional
    public String updateTopic(ForumTopic forumTopic, Long userId){
        log.info("ForumTopicOrm/updateTopic");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumTopic forumTopicAusDB = em.find(ForumTopic.class, forumTopic.getId());
        if(forumTopicAusDB == null) return "Thema nicht in der DB gefunden";

        User creator = forumTopicAusDB.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";
        
        forumTopicAusDB.setTopic(forumTopic.getTopic());

        try{
            em.merge(forumTopicAusDB);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Antwort";
        }
        return "Thema erfolgreich aktualisert";
    }
    /*
        Brief updateTopic
        - ToDo
    */
    @Transactional 
    public String deleteTopic(ForumTopic forumTopic, Long userId){
        log.info("ForumTopicOrm/deleteTopic");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";
        Long topicId = forumTopic.getId();
        ForumTopic forumTopicAusDB = em.find(ForumTopic.class, topicId);
        if(forumTopicAusDB == null) return "Thema nicht in der DB gefunden";

        User creator = forumTopicAusDB.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";
        
        try {
            em.remove(forumTopicAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen des Topics";
        }

        creator.getActivityForum().decTopicCount();
        forumTopicAusDB.getCategory().decTopicCount();
        forumPostOrm.deleteAllPostsFromTopic(topicId);
        
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
            em.createQuery("DELETE ft FROM ForumTopic ft WHERE user_id =: val").setParameter("val", userId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Themen";
        }
      
        //Maybe set count to 0
        // User user = em.find(User.class, userId);
        // user.getActivityForum().setAnswerCount(0L);
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
           User u = forumTopic.getCreator();
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
            em.createQuery("DELETE FROM ForumTopic WHERE category_id =: val").setParameter("val", categoryId).executeUpdate();
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
