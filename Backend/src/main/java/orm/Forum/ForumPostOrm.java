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

import model.User;
import model.Forum.ForumCategory;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;

@ApplicationScoped
public class ForumPostOrm {
    private static final Logger log = Logger.getLogger(ForumPostOrm.class.getName());
    @Inject
    EntityManager em; 
    public List<ForumPost>getAllPosts(){
        log.info("ForumOrm/getPosts");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost p", ForumPost.class);
        return query.getResultList();
    }
    public List<ForumPost>getPostsByUser(Long userId){
         log.info("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE creator := val", ForumPost.class);
        query.setParameter("val",userId);
        return query.getResultList();
    }
    public List<ForumPost>getPostsById(Long postId){
        log.info("ForumOrm/getPostsByUser");
       TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE id := val", ForumPost.class);
       query.setParameter("val",postId);
       return query.getResultList();
   }
    public List<ForumPost>getPostsByEditor(Long userId){
        log.info("ForumOrm/getPostsByEditor");
       TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE editedBy := val", ForumPost.class);
       query.setParameter("val",userId);
       return query.getResultList();
   }
    public List<ForumPost>getPostsByTopic(Long topicId){
        log.info("ForumOrm/getPostsByTopic");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE topic_id := val", ForumPost.class);
        query.setParameter("val",topicId);
        return query.getResultList();
    }
    public List<ForumPost>getPostByTitel(String titel){
        log.info("ForumOrm/getPostByTitel");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE titel := val", ForumPost.class);
        query.setParameter("val",titel);
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
        return "TODO:";
    }
    /**
     *  
     * NOTE: updatePost
     * -    Check permissions. Onyl creator/mods
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
