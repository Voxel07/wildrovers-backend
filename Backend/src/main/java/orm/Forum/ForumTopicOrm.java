package orm.Forum;

import java.util.ArrayList;
//Additional Data Types
import java.util.List;

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
import model.Forum.ForumPost;
import model.Forum.ForumTopic;

@ApplicationScoped
public class ForumTopicOrm {
    
    private static final Logger log = Logger.getLogger(ForumTopicOrm.class.getName());
    @Inject
    EntityManager em; 
    
    @Inject
    ForumCategoryOrm forumCategoryOrm; 

    @Inject
    UserOrm userOrm;

    //Basic GET methods 
    public List<ForumTopic>getAllTopics(){
        log.info("ForumTopicOrm/getTopics");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t", ForumTopic.class);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicById(Long topicId){
        log.info("ForumTopicOrm/getTopicById");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT c FROM ForumTopic c WHERE id =: val", ForumTopic.class);
        query.setParameter("val", topicId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByUser(Long userId){
        log.info("ForumTopicOrm/getTopicByUser");
        /*
        TODO:
            - Add the 
        */
        // User u = em.find(User.class,userId);
        
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t WHERE user_id =: val", ForumTopic.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByCategory(Long categoryId){
        log.info("ForumTopicOrm/getTopicByCategory");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t WHERE category_id =: val", ForumTopic.class);
        query.setParameter("val", categoryId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByTopic(String topic){
        log.info("ForumTopicOrm/getTopicsByTopic");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t WHERE topic =: val", ForumTopic.class);
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
        if(forumCategoryOrm.getCategoriesById(categoryId).isEmpty()){
            log.warning("category not found");
            return "Angegbene Kategorie nicht gefunden";
        }
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
        topic.setCreator(u);
        //addTopic
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
    public String updateTopic(ForumTopic ft, Long userId){
        log.info("ForumTopicOrm/updateTopic");

        return "ToDo";
    }
    /*
        Brief updateTopic
        - ToDo
    */
    @Transactional 
    public String deleteTopic(ForumTopic topicId){
        log.info("ForumTopicOrm/deleteTopic");

        return "ToDo";
    }

    
}
