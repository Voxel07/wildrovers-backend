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

    // Entity reads are intentionally uncached: JPA objects must not be shared between requests.
    //Basic GET methods
    public List<ForumCategory>getAllCategories(){
       log.info("ForumCategoryOrm/getAllCategories");
        TypedQuery<ForumCategory> query = em.createQuery("SELECT c FROM ForumCategory c ORDER BY c.position ASC, c.id ASC", ForumCategory.class);
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
        if(!getCategoriesByName(category.getCategory()).isEmpty()) return Response.status(409).entity("Kategorie existiert bereits").build(); //Check if category already exists

        //get corresponding user from db. Exit if not found
        User user = em.find(User.class, userId);
        if(user == null){
            log.warning("User nicht in der DB gefunden");
            return Response.status(401).entity("User nicht gefunden").build();
        }

        if(category.getVisibility() !=null){
            List<String> allowedVis = Roles.getRoles();

            if(!allowedVis.contains(category.getVisibility())) return Response.status(400).entity("Die angegebene Nutzergruppe existiert nicht").build();

            // Ensure that the user's role rank is greater than or equal to the category's target visibility role
            if(!Roles.hasRequiredRole(user.getRole(), category.getVisibility())) {
                return Response.status(403).entity("Die angegebene Nutzergruppe hat mehr Rechte als deine eigene").build();
            }
        }
        else{
            category.setVisibility("Besucher");
        }

        category.setCreationDate(Time.currentTimeInMillis());

        positionCategory(category, category.getPosition());

        //add Categroy to db
        try {
            em.persist(category);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(500).entity("Error while creating the new Category").build();
        }

        //Update User
        category.setCreator(user);
        category.setTopicCount(0L);
        return Response.status(201).entity("Kategorie erfolgreich erstellt").build();
    }

    /** Rebuilds a contiguous, zero-based order on insert, move and delete, repairing old gaps. */
    private void positionCategory(ForumCategory category, Long requestedPosition) {
        List<ForumCategory> ordered = new java.util.ArrayList<>(em.createQuery(
                "SELECT c FROM ForumCategory c ORDER BY c.position ASC, c.id ASC", ForumCategory.class)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getResultList());
        int oldIndex = ordered.indexOf(category);
        ordered.remove(category);
        int index = requestedPosition == null ? (oldIndex < 0 ? ordered.size() : oldIndex)
                : (int) Math.max(0, Math.min(requestedPosition, ordered.size()));
        ordered.add(index, category);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition((long) i);
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "forum-categories"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-name"),
        @CacheInvalidateAll(cacheName = "forum-categories-by-id"),
        @CacheInvalidateAll(cacheName = "forum-category-count")
    })
    public Response updateCategory(ForumCategory forumCategory, Long userId){
        log.info("ForumCategoryOrm/updateCategory");

        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();

        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, forumCategory.getId());
        if(forumCategoryAusDB == null) return Response.status(404).entity("Kategorie nicht in der DB gefunden").build();

        User creator = forumCategoryAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();


        // Update visibility if set
        if (forumCategory.getVisibility() != null) {
            List<String> allowedVis = Roles.getRoles();
            if (allowedVis.contains(forumCategory.getVisibility())) {
                if (Roles.hasRequiredRole(user.getRole(), forumCategory.getVisibility())) {
                    forumCategoryAusDB.setVisibility(forumCategory.getVisibility());
                } else {
                    return Response.status(403).entity("Die angegebene Nutzergruppe hat mehr Rechte als deine eigene").build();
                }
            } else {
                return Response.status(400).entity("Die angegebene Nutzergruppe existiert nicht").build();
            }
        }

        forumCategoryAusDB.setCategory(forumCategory.getCategory());
        positionCategory(forumCategoryAusDB, forumCategory.getPosition());

        try{
            em.merge(forumCategoryAusDB);
        }catch(Exception e){
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(500).entity("Fehler beim updaten der Kategorie").build();
        }
        return Response.status(200).entity("Kategorie erfolgreich aktualisert").build();
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
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();
        Long categoryId = forumCategory.getId();
        ForumCategory forumCategoryAusDB = em.find(ForumCategory.class, categoryId);
        if(forumCategoryAusDB == null) return Response.status(404).entity("Kategorie nicht in der DB gefunden").build();

        User creator = forumCategoryAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        try {
            forumTopicOrm.deleteAllTopicsFromCategory(categoryId);
            em.remove(forumCategoryAusDB);
            em.flush();
            List<ForumCategory> remaining = em.createQuery(
                    "SELECT c FROM ForumCategory c ORDER BY c.position ASC, c.id ASC", ForumCategory.class)
                    .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getResultList();
            for (int i = 0; i < remaining.size(); i++) remaining.get(i).setPosition((long) i);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(500).entity("Fehler beim Löschen der Kategorie").build();
        }

        return Response.status(200).entity("Kategorie erfolgreich gelöscht").build();
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
