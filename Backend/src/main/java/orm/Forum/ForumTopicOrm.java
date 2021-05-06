package orm.Forum;

//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.validation.constraints.Null;

import java.util.logging.Level;
//Logging
import java.util.logging.Logger;

//Zeit
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    

import model.Forum.ForumCategory;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import model.User;

import orm.Forum.ForumCategoryOrm;

@ApplicationScoped
public class ForumTopicOrm {
    
    private static final Logger log = Logger.getLogger(ForumTopicOrm.class.getName());
    @Inject
    EntityManager em; 

    ForumCategoryOrm forumCategoryOrm; 

    //Basic GET methods 
    public List<ForumTopic>getAllTopics(){
        log.info("ForumTopicOrm/getTopics");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t", ForumTopic.class);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByUser(Long userId){
        log.info("ForumTopicOrm/getTopicByUser");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t WHERE user_id := vla", ForumTopic.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByCategory(Long categoryId){
        log.info("ForumTopicOrm/getTopicByCategory");
        TypedQuery<ForumTopic> query = em.createQuery("SELECT t FROM ForumTopic t WHERE category_id := vla", ForumTopic.class);
        query.setParameter("val", categoryId);
        return query.getResultList();
    }
    public List<ForumTopic>getTopicsByTopic(String topic){
        log.info("ForumTopicOrm/getTopicByCategory");
        var query = em.createQuery("SELECT t FROM ForumTopic t WHERE topic := vla", ForumTopic.class);
        query.setParameter("val", topic);
        return query.getResultList();
    }
    //CRUD operations for Froum Categorys
    /*
        Brief addTopic
        - Checks if the Topic allready exists
        - Check if passed Category exists
        - Create a formated Time string from the local Server time
        - returns success or the error that accured as a string
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
            return "Error while creating the new topic";
        }
        return "Kategorie erfolgreich erstellt";
    }
    /*
        Brief updateTopic
        - ToDo
    */
    @Transactional
    public String updateTopic(){
        return "ToDo";
    }
    /*
        Brief updateTopic
        - ToDo
    */
    @Transactional String updateTopic(Long topicId){
        return "ToDo";
    }

    
}
