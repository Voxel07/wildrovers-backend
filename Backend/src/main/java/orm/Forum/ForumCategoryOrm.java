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
import model.User;

@ApplicationScoped
public class ForumCategoryOrm {

    private static final Logger log = Logger.getLogger(ForumCategoryOrm.class.getName());
    @Inject
    EntityManager em; 

    //Basic GET methods 
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
    //CRUD operations for Froum Categorys
    /*
        Brief addCategory
        - Checks if the category name allready exists
        - Creates a formated Time stinrg from the local Server time
        - returns success or the error that accured as a string
    */
    @Transactional
    public String addCategory(ForumCategory category, Long userId){
         log.info("ForumOrm/addCategory");

        //Check if exists
        if(!getCategoriesByName(category.getCategory()).isEmpty()) return "Kategorie exestiert bereits";
        //setCreationDate        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        category.setCreationDate(dtf.format(now));
        //get corresponding user from db. Exit if not found
        User u = em.find(User.class, userId);
        if(u == null){
            log.warning("User nicht in der DB gefunden");
            return "User nicht gefunden";
        } 
        category.setCreator(u);

        //add Categroy to db
        try {
            em.persist(category);
        } catch (Exception e) {
             log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Error while creating the new Category";
        }
        return "Kategorie erfolgreich erstellt";
    }
    /*
        @Brief updateCategory
        - ToDo: Only Creator/Mod is allowed to update
    */
    @Transactional
    public String updateCategory(ForumCategory category){
        log.info("ForumOrm/updateCategory");

        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE category =: val OR id =: val2",ForumCategory.class);
        query.setParameter("val", category.getCategory());
        query.setParameter("val2", category.getId());
        List<ForumCategory> ausdb = query.getResultList();
        //Check if exists
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
        return "Kategorie erfolgreich aktualisiert";
    }
    /*
        @Brief removeCategory
        - ToDo everything
    */
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
}
