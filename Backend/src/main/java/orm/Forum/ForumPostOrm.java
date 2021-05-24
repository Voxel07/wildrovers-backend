package orm.Forum;

//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.sound.midi.SysexMessage;
import javax.transaction.Transactional;


//Logging
import java.util.logging.Logger;
import java.util.logging.Level;
//Zeit
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;

import model.User;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;

@ApplicationScoped
public class ForumPostOrm {
    private static final Logger log = Logger.getLogger(ForumPostOrm.class.getName());
    @Inject
    EntityManager em; 
    @Inject
    ForumTopicOrm forumTopicOrm;


    public List<ForumPost>getAllPosts(){
        log.info("ForumOrm/getPosts");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp", ForumPost.class);
        return query.getResultList();
    }
    public List<ForumPost>getPostsByUser(Long userId){
         log.info("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE user_id =: val", ForumPost.class);
        query.setParameter("val",userId);
        return query.getResultList();
    }
    public List<ForumPost>getPostsById(Long postId){
        log.info("ForumOrm/getPostsById");
       TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE id =: val", ForumPost.class);
       query.setParameter("val",postId);
       return query.getResultList();
   }
    public List<ForumPost>getPostsByEditor(Long userId){
        log.info("ForumOrm/getPostsByEditor");
       TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE  editor_id =: val", ForumPost.class);
       query.setParameter("val",userId);
       return query.getResultList();
   }
    public List<ForumPost>getPostsByTopic(Long topicId){
        log.info("ForumOrm/getPostsByTopic");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE topic_id =: val", ForumPost.class);
        query.setParameter("val",topicId);
        return query.getResultList();
    }
    public List<ForumPost>getPostByTitel(String title){
        log.info("ForumOrm/getPostByTitel");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE title =: val", ForumPost.class);
        query.setParameter("val",title);
        return query.getResultList();
    }
    //Crud operations for ForumPosts
    /**
     *  NOTE: addPost
        -   Checks if Post titel already exists in the same topic
        - 
     * @param forumPost Conaines all Content of the Post aka text and picutres
     * @param topicId 
     * @param userId
     * @return
     */
    @Transactional
    public String addPost(ForumPost forumPost,Long topicId,Long userId){
        log.info("ForumPostOrm/addPost");
        if(topicId == null) return "Es muss ein Tehma angegeben werden";
        if(userId == null) return "Es muss ein User angegebene werden";

        List<ForumTopic> forumTopics = forumTopicOrm.getTopicById(topicId);
        if(forumTopics.isEmpty()) return "Topic nicht gefunden";
        ForumTopic topic = forumTopics.get(0);
        // if(topic == null){
        //     log.warning("TOPIC not found");
        //     return "Das angegebene Tehma existiert nicht";
        // }
        //Check if Post title exists in current Topic
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE topic_id =: val AND title =: val2",ForumPost.class);
        query.setParameter("val", topicId);
        query.setParameter("val2", forumPost.getTitle());
        if(!query.getResultList().isEmpty()) return "Ein Post mit diesem Titel exestiert bereit in diesem Thema";

        User user = em.find(User.class,userId);
        if(user == null) return "Der angegebene Nutzer wurde nicht gefunden";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        forumPost.setCreationDate(dtf.format(now));

        user.getActivityForum().incPostCount();
        topic.incrementPostCount();
        forumPost.setTopic(topic);
        forumPost.setCreator(user);
        forumPost.setAnswerCount(0L);
        forumPost.setDislikes(0L);
        forumPost.setLikes(0L);
        forumPost.setViews(0L);

        // try {
        //     em.merge(user);
        // } catch (Exception e) {
        //     log.log(Level.SEVERE, "Result{0}", e.getMessage());
        //     return "Fehler beim aktualiseren des Users";
        // }

        // try {
        //     em.merge(topic);
        // } catch (Exception e) {
        //     log.log(Level.SEVERE, "Result{0}", e.getMessage());
        //     return "Fehler beim aktualiseren des Themas";
        // }

        try {
            em.persist(forumPost);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim erstellen des Posts";
        }

        return "Post erfolgreich erstellt";
    }
    /**
     *  
     * NOTE: updatePost
     * -    Check permissions. Only creator/mods
     * -    Check if new name exists already exists in the topic   
     * @param forumPost
     * @param userId
     * @return
     */
    @Transactional
    public String updatePost(ForumPost forumPost, Long userId){
        return "TODO:";
    }
    /**
     * 
     * @param forumPost
     * @param userId
     * @return
     */
    @Transactional
    public String deletePost(ForumPost forumPost, Long userId){
        return "TODO:";
    }


}
