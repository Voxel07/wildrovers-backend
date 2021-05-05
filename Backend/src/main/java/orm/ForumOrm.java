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

import java.util.logging.Level;
//Logging
import java.util.logging.Logger;

//Zeit
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    

import model.Forum.ForumCategory;
import model.Forum.ForumPost;
import model.User;

@ApplicationScoped
public class ForumOrm {
    private static final Logger log = Logger.getLogger(ForumOrm.class.getName());
    @Inject
    EntityManager em; 

    //Basic Get methoeds 
    public List<ForumCategory>getAllCategories(){
       log.info("ForumOrm/getAllCategories");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c", ForumCategory.class);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesByName(String category){
         log.info("ForumOrm/getCategoriesByName");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE category =: val", ForumCategory.class);
        query.setParameter("val", category);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesById(Long id){
         log.info("ForumOrm/getCategoriesById");
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
         log.info("ForumOrm/addCategory");
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
             log.log(java.util.logging.Level.SEVERE, "Result{0}", e.getMessage());
            return "Error while creating the new Category";
        }
        //Create
        return "Kategorie erfolgreich erstellt";
    }
    public String updateCategory(ForumCategory category){
         log.info("ForumOrm/updateCategory");
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
             log.info("Exception updateCategory" + e.getMessage());
            return "Error while updating the Category";
        }
        return "ToDO";
    }
    public String removeCategory(ForumCategory category){
         log.info("ForumOrm/removeCategory");
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

    public List<ForumPost>getPostsByCategory(String category){
         log.info("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT f FROM ForumPost WHERE category := val", ForumPost.class);
        query.setParameter("val",category);
        return query.getResultList();
    }

   

}
