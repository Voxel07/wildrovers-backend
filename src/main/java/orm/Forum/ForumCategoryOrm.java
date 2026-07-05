package orm.Forum;

//Datentypen
import java.util.List;

//
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.persistence.Query;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidateAll;


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
    @CacheResult(cacheName = "forum-categories")
    public List<ForumCategory>getAllCategories(){
       log.info("ForumCategoryOrm/getAllCategories");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c ORDER BY position ASC", ForumCategory.class);
        return query.getResultList();
    }
    @CacheResult(cacheName = "forum-categories-by-name")
    public List<ForumCategory>getCategoriesByName(String category){
        log.info("ForumCategoryOrm/getCategoriesByName");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE category =: val", ForumCategory.class);
        query.setParameter("val", category);
        return query.getResultList();
    }
    @CacheResult(cacheName = "forum-categories-by-id")
    public List<ForumCategory>getCategoriesById(Long id){
        log.info("ForumCategoryOrm/getCategoriesById");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c WHERE id =: val", ForumCategory.class);
        query.setParameter("val", id);
        return query.getResultList();
    }
    @CacheResult(cacheName = "forum-category-count")
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
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "forum-categories"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-name"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-id"),
        @CacheInvalidateAll(cacheName = "forum-category-count")
    })
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

            if(!allowedVis.contains(category.getVisibility())) return Response.status(406).entity("Die angegebene Nutzergruppe existiert nicht").build();

            // Ensure that the user's role rank is greater than or equal to the category's target visibility role
            if(!Roles.hasRequiredRole(user.getRole(), category.getVisibility())) {
                return Response.status(406).entity("Die angegebene Nutzergruppe hat mehr Rechte als deine eigene").build();
            }
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
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "forum-categories"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-name"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-id"),
        @CacheInvalidateAll(cacheName = "forum-category-count")
    })
    public String updateCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/updateCategory");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, forumCategory.getId());
        if(forumCategoryAusDB == null) return "Kategorie nicht in der DB gefunden";

        User creator = forumCategoryAusDB.getCreatorObj();
        if (creator == null && !user.getRole().equals("Admin")) return "Nur Admins dürfen verwaiste Einträge bearbeiten";

        if (creator != null && !creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        forumCategoryAusDB.setCategory(forumCategory.getCategory());

        // Update visibility if set
        if (forumCategory.getVisibility() != null) {
            List<String> allowedVis = Roles.getRoles();
            if (allowedVis.contains(forumCategory.getVisibility())) {
                if (Roles.hasRequiredRole(user.getRole(), forumCategory.getVisibility())) {
                    forumCategoryAusDB.setVisibility(forumCategory.getVisibility());
                } else {
                    return "Die angegebene Nutzergruppe hat mehr Rechte als deine eigene";
                }
            } else {
                return "Die angegebene Nutzergruppe existiert nicht";
            }
        }

        // Update position if set and changed
        if (forumCategory.getPosition() != null && !forumCategory.getPosition().equals(forumCategoryAusDB.getPosition())) {
            forumCategoryAusDB.setPosition(forumCategory.getPosition());
            if (!positionCategory(forumCategoryAusDB)) {
                return "Reihenfolge konnte nicht geändert werden";
            }
        }

        try{
            em.merge(forumCategoryAusDB);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Kategorie";
        }
        return "Kategorie erfolgreich aktualisert";
    }
    /**
     * Deletes a category and all its nested topics/posts/answers.
     * Only the category creator or an Admin may delete a category.
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "forum-categories"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-name"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-id"),
        @CacheInvalidateAll(cacheName = "forum-category-count")
    })
    public Response deleteCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/removeCategory");
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(406).entity("User nicht gefunden").build();
        Long categoryId = forumCategory.getId();
        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, categoryId);
        if(forumCategoryAusDB == null) return Response.status(406).entity("Kategorie nicht in der DB gefunden").build();

        User creator = forumCategoryAusDB.getCreatorObj();
        if (creator == null && !user.getRole().equals("Admin")) return Response.status(406).entity("Nur Admins dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !user.getRole().equals("Admin")) return Response.status(406).entity("Nur der Ersteller oder Mods dürfen das").build();

        try {
            forumTopicOrm.deleteAllTopicsFromCategory(categoryId);
            em.remove(forumCategoryAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(406).entity("Fehler beim Löschen der Kategorie").build();
        }

        return Response.status(200).entity("Kategorie erfolgreich gelöscht").build();
    }

    //Custom SQL Querys
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "forum-categories"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-name"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-id"),
        @CacheInvalidateAll(cacheName = "forum-category-count")
    })
    public int insertCategory(long position)
    {
        log.info("ForumCategoryOrm/insertCategory");
        Query query = em.createNativeQuery("UPDATE FORUM_CATEGORY SET position = position + 1 WHERE position >= :val", ForumCategory.class);
        query.setParameter("val", position);
        return query.executeUpdate();
    }
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "forum-categories"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-name"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-id"),
        @CacheInvalidateAll(cacheName = "forum-category-count")
    })
    public void invalidateCategoryCaches(){
        log.info("ForumCategoryOrm/invalidateCategoryCaches");
    }
}
