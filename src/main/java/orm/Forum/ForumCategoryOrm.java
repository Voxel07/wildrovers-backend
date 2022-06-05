package orm.Forum;

//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.persistence.Query;


import java.util.logging.Level;
//Logging
import java.util.logging.Logger;

//Zeit
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

//Custom stuff
import model.User;
import model.Forum.ForumCategory;
import helper.CustomHttpResponse;

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
    public long getCategoryCnt(){
        log.info("ForumCategoryOrm/getCategoryCnt");
        Query query = em.createQuery("SELECT COUNT(*) FROM ForumCategory");
        return (Long)query.getSingleResult();
    }

    //CRUD operations for Froum Categorys
    /*
        NOTE: addCategory
        - Checks if the category name allready exists
        - Creates a formated Time stinrg from the local Server time
        - returns success or the error that accured as a string
    */
    @Transactional
    public CustomHttpResponse addCategory(ForumCategory category, Long userId){
        log.info("ForumCategoryOrm/addCategory");

        if(category.getCategory() == null) return new CustomHttpResponse(555,"Kategorie nicht gesetzt"); //Check if Values are set
        if(category.getCategory().length() < 4) return new CustomHttpResponse(555,"Kategorie zu kurz"); //Check length requirment
        if(!getCategoriesByName(category.getCategory()).isEmpty()) return new CustomHttpResponse(555,"Kategorie exestiert bereits"); //Check if category already exists

        //get corresponding user from db. Exit if not found
        User u = em.find(User.class, userId);
        if(u == null){
            log.warning("User nicht in der DB gefunden");
            return new CustomHttpResponse(555,"User nicht gefunden");
        }

        //setCreationDate
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        category.setCreationDate(dtf.format(now));

        //Position stuff
        long numOfExistingCat = getCategoryCnt();
        long position;
        boolean rearangeCategory = false;
        //Check if a Position is set
        if(category.getPosition() == null){
            category.setPosition(numOfExistingCat);
        }
        else{ //Ensure that the postion is valid
            position = category.getPosition();
            if(numOfExistingCat < position){ //Set Number was to big, new Entry will be placed at the end
                category.setPosition(numOfExistingCat);
            }
            else if(numOfExistingCat == position){
                //nothing to do
            }
            else{
                rearangeCategory = true;
            }
        }

        //Update the category order if necessary
        if(rearangeCategory){
            try {
                insertCategory(category.getPosition());
            } catch (Exception e) {
                log.log(Level.SEVERE, "Result{0}", e.getMessage());
                return new CustomHttpResponse(555,"Reihenfolge konnte nicht geändert werden");
            }
        }

        //add Categroy to db
        try {
            em.persist(category);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return new CustomHttpResponse(555,"Error while creating the new Category");
        }

        //Update User
        u.getActivityForum().incCategoryCount();
        category.setCreator(u);
        category.setTopicCount(0L);
        return new CustomHttpResponse(200,"Kategorie erfolgreich erstellt");
    }

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

    //Custom SQL Querys
    public int insertCategory(long position)
    {
        log.info("ForumCategoryOrm/insertCategory");
        Query query = em.createNativeQuery("UPDATE FORUM_CATEGORY SET position = position + 1 WHERE position >= :val", ForumCategory.class);
        query.setParameter("val", position);
        return query.executeUpdate();
    }
}
