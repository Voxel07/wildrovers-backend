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
    public List<ForumPost>getPostsByEditor(Long userId){
        log.info("ForumOrm/getPostsByEditor");
       TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE editedBy := val", ForumPost.class);
       query.setParameter("val",userId);
       return query.getResultList();
   }
    public List<ForumPost>getPostsByCategory(Long categoryId){
        log.info("ForumOrm/getPostsByCategory");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE category := val", ForumPost.class);
        query.setParameter("val",categoryId);
        return query.getResultList();
    }
    public List<ForumPost>getPostByTitel(String titel){
        log.info("ForumOrm/getPostByTitel");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE titel := val", ForumPost.class);
        query.setParameter("val",titel);
        return query.getResultList();
    }

}
