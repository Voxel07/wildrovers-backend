package orm;

//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import model.Forum.ForumPost;

@ApplicationScoped
public class ForumOrm {
    @Inject
    EntityManager em; 

    public List<ForumPost>getPosts(){
        System.out.println("ForumOrm/getPosts");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost p", ForumPost.class);
        return query.getResultList();
    }

    public List<ForumPost>getPostsByUser(Long userId){
        System.out.println("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE creator := val", ForumPost.class);
        query.setParameter("val",userId);
        return query.getResultList();
    }

    public List<ForumPost>getPostsBycategory(String category){
        System.out.println("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE category := val", ForumPost.class);
        query.setParameter("val",category);
        return query.getResultList();
    }

}
