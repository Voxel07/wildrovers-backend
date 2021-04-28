package orm;

import java.sql.Date;
//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.validation.constraints.Null;



//Zeit
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    

import model.Forum.ForumCategory;
import model.Forum.ForumPost;
import model.User;

@ApplicationScoped
public class ForumOrm {
    @Inject
    EntityManager em; 

    //Basic Get methoeds 
    public List<ForumCategory>getAllCategories(){
        System.out.println("ForumOrm/getAllCategories");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c", ForumCategory.class);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesByName(String category){
        System.out.println("ForumOrm/getCategoriesByName");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE category =: val", ForumCategory.class);
        query.setParameter("val", category);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesById(Long id){
        System.out.println("ForumOrm/getCategoriesById");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE id =: val", ForumCategory.class);
        query.setParameter("val", id);
        return query.getResultList();
    }
    //Crud operations for Froum Categorys
    /*
        Brief addCategory
        - Checks if the category name allready exists
        - Creates a formated Time stinrg from the local Server time
        - returns success or the error that accured as a string
    */
    @Transactional
    public String addCategory(ForumCategory category, Long categorId){
        System.out.println("ForumOrm/addCategory");
        //Check if exists
        // TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory WHERE category =: val", ForumCategory.class);
        // query.setParameter("val", category.getCategory());
        if(!getCategoriesByName(category.getCategory()).isEmpty()) return "Kategorie exestiert bereits";
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        category.setCreationDate(dtf.format(now));
        
        User u = em.find(User.class, categorId);
        if(u == null) return "User nicht gefunden";
        category.setCreator(u);
        
        try {
            em.persist(category);
        } catch (Exception e) {
            System.out.println("Exception addCategory" + e.getMessage());
            return "Error while creating the new Category";
        }
        //Create
        return "Kategorie erfolgreich erstellt";
    }
    public String updateCategory(ForumCategory category){
        System.out.println("ForumOrm/updateCategory");
        //Check if exists
        // if(getCategoriesById(category.getId()).isEmpty()) return "Kategorie exestiert nicht";
        // if((!getCategoriesByName(category.getCategory()).isEmpty())&&)
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE category =: val OR id =: val2",ForumCategory.class);
        query.setParameter("val", category.getCategory());
        query.setParameter("val2", category.getId());
        List<ForumCategory> ausdb = query.getResultList();
        
        if(ausdb.isEmpty()) return "Kategorie exestiert nicht";

        for (ForumCategory aktCat : ausdb) {
            if(aktCat.getCategory().equals(category.getCategory()) && !aktCat.getId().equals(category.getId())) return "Ketegoriename ist bereits vergeben";
        }
        //Update
        try {
            em.merge(category);
        } catch (Exception e) {
            System.out.println("Exception updateCategory" + e.getMessage());
            return "Error while updating the Category";
        }
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
