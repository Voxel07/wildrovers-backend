package orm.Forum;

//Datentypen
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

//Zeit
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;

import model.User;
import model.Forum.ForumCategory;

@ApplicationScoped
public class ForumCategoryOrm {

    private static final Logger log = Logger.getLogger(ForumCategoryOrm.class.getName());
    @Inject
    EntityManager em; 
    @Inject
    ForumTopicOrm forumTopicOrm;

    //Basic GET methods 
    public List<ForumCategory>getAllCategories(){
       log.info("ForumCategoryOrm/getAllCategories");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c", ForumCategory.class);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesByName(String category){
        log.info("ForumCategoryOrm/getCategoriesByName");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE category =: val", ForumCategory.class);
        query.setParameter("val", category);
        return query.getResultList();
    }
    public List<ForumCategory>getCategoriesById(Long id){
        log.info("ForumCategoryOrm/getCategoriesById");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE id =: val", ForumCategory.class);
        query.setParameter("val", id);
        return query.getResultList();
    }
    //CRUD operations for Froum Categorys
    /*
        NOTE: addCategory
        - Checks if the category name allready exists
        - Creates a formated Time stinrg from the local Server time
        - returns success or the error that accured as a string
    */
    @Transactional
    public String addCategory(ForumCategory category, Long userId){
         log.info("ForumCategoryOrm/addCategory");

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
        u.getActivityForum().incCategoryCount();
        category.setCreator(u);
        category.setTopicCount(0L);

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
        - TODO: Only Creator/Mod is allowed to update
    */
    @Transactional
    public String updateCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/updateCategory");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, forumCategory.getId());
        if(forumCategoryAusDB == null) return "Kategorie nicht in der DB gefunden";

        User creator = forumCategoryAusDB.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";
        
        forumCategoryAusDB.setCategory(forumCategory.getCategory());

        try{
            em.merge(forumCategoryAusDB);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Kategorie";
        }
        return "Kategorie erfolgreich aktualisert";
    }
    /*
        @Brief removeCategory
        - TODO: everything
    */
    @Transactional
    public String deleteCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/removeCategory");
        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";
        Long categoryId = forumCategory.getId();
        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, categoryId);
        if(forumCategoryAusDB == null) return "Thema nicht in der DB gefunden";

        User creator = forumCategoryAusDB.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";
        
        try {
            em.remove(forumCategoryAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Kategorie";
        }

        creator.getActivityForum().decCategoryCount();
        forumTopicOrm.deleteAllTopicsFromCategory(categoryId);
        user.getActivityForum().decCategoryCount();
        
        return "Kategorie erfolgreich gelöscht";
    }
}
