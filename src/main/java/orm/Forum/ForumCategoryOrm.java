package orm.Forum;

//Datentypen
import java.util.List;

//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.ws.rs.core.Response;
import javax.persistence.Query;


import java.util.logging.Level;
//Logging
import java.util.logging.Logger;

//Zeit
import tools.Time;

//Custom stuff
import model.User;
import model.Forum.ForumCategory;
import model.Users.Roles;
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
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c ORDER BY position ASC", ForumCategory.class);
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
    public Response addCategory(ForumCategory category, Long userId){
        log.info("ForumCategoryOrm/addCategory");
        if(!getCategoriesByName(category.getCategory()).isEmpty()) return Response.status(406).entity("Kategorie existiert bereits").build(); //Check if category already exists

        //get corresponding user from db. Exit if not found
        User user = em.find(User.class, userId);
        if(user == null){
            log.warning("User nicht in der DB gefunden");
            return Response.status(555).entity("User nicht gefunden").build();
        }

        if(category.getVisibility() !=null){

            List<String> allowedVis = Roles.getRoles();

            if(!allowedVis.contains(category.getVisibility())) return Response.status(406).entity("Die angeebene Nutzergrupper existiert nicht").build();

            //Should never be triggered. Role is saved in the jwt
            int index = allowedVis.indexOf(user.getRole());
            if (index == -1) return Response.status(406).entity("Pfusch nicht an deinem Nutzer rum").build();


            //Ensure that the user has the same rights as the visibilty groub
            for(int vis = index ; vis < allowedVis.size(); vis++){
                allowedVis.remove(index+1);
            }

            if(!allowedVis.contains(category.getVisibility())) return Response.status(406).entity("Die angegebene Nutzergruppe hat mehr Rechte als deine eigen").build();
        }
        else{
            category.setVisibility("Besucher");
        }

        category.setCreationDate(Time.currentTimeInMillis());

        if (!positionCategory(category)) return Response.status(406).entity("Reihenfolge konnte nicht geändert werden").build();

        //add Categroy to db
        try {
            em.persist(category);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(406).entity("Error while creating the new Category").build();
        }

        //Update User
        user.getActivityForum().incCategoryCount();
        category.setCreator(user);
        category.setTopicCount(0L);
        return Response.status(201).entity("Kategorie erfolgreich erstellt").build();
    }

    /*
     * Helper function for AddCategory
     * This ensures that the "POSITION" value is handled correctly
     */
    private boolean positionCategory(ForumCategory category){
        //Position stuff
        long numOfExistingCat = getCategoryCnt();
        long position;
        boolean rearangeCategory = false;
        //Check if a Position is set
        if(category.getPosition() == null){
            category.setPosition(numOfExistingCat);
        }
        else //Ensure that the postion is valid
        {
            position = category.getPosition();
            if(numOfExistingCat < position){ //Set Number was to big, new Entry will be placed at the end
                category.setPosition(numOfExistingCat+1);
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
                return false;
            }
        }
        return true;
    }

    @Transactional
    public String updateCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/updateCategory");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, forumCategory.getId());
        if(forumCategoryAusDB == null) return "Kategorie nicht in der DB gefunden";

        User creator = forumCategoryAusDB.getCreatorObj();
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
    public Response deleteCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/removeCategory");
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(406).entity("User nicht gefunden").build();
        Long categoryId = forumCategory.getId();
        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, categoryId);
        if(forumCategoryAusDB == null) return Response.status(406).entity("Kategorie nicht in der DB gefunden").build();

        User creator = forumCategoryAusDB.getCreatorObj();
        if (creator == null) return Response.status(406).entity("Ersteller nicht gesetzt").build();

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return Response.status(406).entity("Nur der Ersteller oder Mods dürfen das").build();

        try {
            em.remove(forumCategoryAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(406).entity("Fehler beim Löschen der Kategorie").build();
        }

        creator.getActivityForum().decCategoryCount();
        forumTopicOrm.deleteAllTopicsFromCategory(categoryId);
        user.getActivityForum().decCategoryCount();

        return Response.status(200).entity("Kategorie erfolgreich gelöscht").build();
    }

    //Custom SQL Querys
    public int insertCategory(long position)
    {
        log.info("ForumCategoryOrm/insertCategory");
        Query query = em.createNativeQuery("UPDATE FORUM_CATEGORY SET position = position + 1 WHERE position >= :val", ForumCategory.class);
        query.setParameter("val", position);
        return query.executeUpdate();
    }
    public int updateCategoryUserName(String newUserName, String oldUserName){
        log.info("ForumCategoryOrm/updateCategoryUserName");
        Query query = em.createNativeQuery("UPDATE FORUM_CATEGORY SET userName =:newUserName WHERE userName =:oldUserName", ForumCategory.class);
        query.setParameter("newUserName", newUserName);
        query.setParameter("oldUserName", oldUserName);
        return query.executeUpdate();
    }
}
