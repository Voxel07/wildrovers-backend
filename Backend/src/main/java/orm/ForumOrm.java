package orm;

//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import model.Forum.ForumCategory;
import model.Forum.ForumPost;

@ApplicationScoped
public class ForumOrm {
    @Inject
    EntityManager em; 

    public List<ForumCategory>getAllCategories(){
        System.out.println("ForumOrm/getAllCategories");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory", ForumCategory.class);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesByName(String categroy){
        System.out.println("ForumOrm/getCategoriesByName");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory WHERE categroy := val", ForumCategory.class);
        query.setParameter("val", categroy);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesById(Long id){
        System.out.println("ForumOrm/getCategoriesById");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory WHERE id := val", ForumCategory.class);
        query.setParameter("val", id);
        return query.getResultList();
    }
    public String addCategory(ForumCategory category){
        System.out.println("ForumOrm/addCategory");
        //Check if exists
        //Create
        return "ToDo";
    }
    public String updateCategory(ForumCategory category){
        System.out.println("ForumOrm/updateCategory");
        //Check if exists
        //Update
        return "ToDO";
    }
    public String removeCategory(ForumCategory category){
        System.out.println("ForumOrm/removeCategory");
        //Check if exists
        //Get all Topics
        //Get all Posts
        //Get all Ansers
        //Delete Answers
        //Delete Topics
        //Delete Category
        return "ToDo";
    }

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

    public List<ForumPost>getPostsByCategory(String category){
        System.out.println("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE category := val", ForumPost.class);
        query.setParameter("val",category);
        return query.getResultList();
    }

   

}
