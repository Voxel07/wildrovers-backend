package orm.Forum;

import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;

//
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

//Logging

//Time
import tools.Time;
import tools.HtmlSanitizer;

import model.User;
import model.Forum.ForumCategory;
import model.Forum.ForumTopic;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;

@ApplicationScoped
public class ForumTopicOrm {

    @Inject
    EntityManager em;

    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    HtmlSanitizer htmlSanitizer;



    //Basic GET methods
    @WithSpan
    public List<ForumTopic>getAllTopics()
    {
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category", ForumTopic.class);
        return query.getResultList();
    }

    /**
     * Paged topic list for one reader. Only one filter is applied, in the order id, name, user,
     * category. Topics are listed in creation order.
     */
    @WithSpan
    public ForumPage<ForumTopic> findTopics(Long topicId, String topicName, Long userId, Long categoryId,
            List<String> visibleRoles, Integer page, Integer size) {
        String filter = "";
        Object value = null;
        if (topicId != null) { filter = " AND ft.id = :val"; value = topicId; }
        else if (topicName != null) { filter = " AND ft.topic = :val"; value = topicName; }
        else if (userId != null) { filter = " AND ft.creator.id = :val"; value = userId; }
        else if (categoryId != null) { filter = " AND c.id = :val"; value = categoryId; }
        String where = " WHERE " + ForumPage.visibilityClause("c", visibleRoles) + filter;

        TypedQuery<ForumTopic> query = em.createQuery(
                "SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category c" + where
                        + " ORDER BY ft.creationDate ASC, ft.id ASC", ForumTopic.class);
        TypedQuery<Long> count = em.createQuery(
                "SELECT COUNT(ft) FROM ForumTopic ft LEFT JOIN ft.category c" + where, Long.class);
        ForumPage.bindVisibility(query, visibleRoles);
        ForumPage.bindVisibility(count, visibleRoles);
        if (value != null) {
            query.setParameter("val", value);
            count.setParameter("val", value);
        }
        query.setFirstResult(ForumPage.firstResult(page, size));
        query.setMaxResults(ForumPage.pageSize(size));
        return new ForumPage<>(query.getResultList(), count.getSingleResult());
    }

    @WithSpan
    public List<ForumTopic>getTopicById(Long topicId)
    {
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category WHERE ft.id =: val", ForumTopic.class);
        query.setParameter("val", topicId);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumTopic>getTopicsByUser(Long userId)
    {
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category WHERE ft.creator.id = :val", ForumTopic.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumTopic>getTopicsByCategory(Long categoryId)
    {
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft LEFT JOIN FETCH ft.category WHERE ft.category.id = :val", ForumTopic.class);
        query.setParameter("val", categoryId);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumTopic>getTopicsByTopic(String topic)
    {
        TypedQuery<ForumTopic> query = em.createQuery("SELECT ft FROM ForumTopic ft WHERE topic =: val", ForumTopic.class);
        query.setParameter("val", topic);
        return query.getResultList();
    }

    /** Topic names are unique per category; {@code excludeId} skips the topic being renamed. */
    private boolean topicNameTaken(String topic, Long categoryId, Long excludeId) {
        TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(ft) FROM ForumTopic ft WHERE ft.topic = :topic AND ft.category.id = :cat"
                        + (excludeId != null ? " AND ft.id <> :id" : ""), Long.class)
                .setParameter("topic", topic)
                .setParameter("cat", categoryId);
        if (excludeId != null) query.setParameter("id", excludeId);
        return query.getSingleResult() > 0;
    }

    /**
     * Fills the newest post and the read state of each topic with two queries for the whole
     * list. Anonymous readers have no read state, so topics with posts count as unread for them.
     */
    public void attachSummaries(List<ForumTopic> topics, Long userId) {
        List<Long> ids = topics.stream().map(ForumTopic::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return;

        java.util.Map<Long, ForumTopic.LatestPost> latest = new HashMap<>();
        List<Object[]> rows = em.createQuery(
                "SELECT p.topic.id, p.id, p.title, c.userName, p.creationDate FROM ForumPost p LEFT JOIN p.creator c "
                        + "WHERE p.topic.id IN :ids AND p.creationDate = "
                        + "(SELECT MAX(p2.creationDate) FROM ForumPost p2 WHERE p2.topic.id = p.topic.id) "
                        + "ORDER BY p.id DESC", Object[].class)
                .setParameter("ids", ids)
                .getResultList();
        for (Object[] row : rows) {
            latest.putIfAbsent((Long) row[0], new ForumTopic.LatestPost((Long) row[1], (String) row[2],
                    row[3] != null ? (String) row[3] : "deleted", (Long) row[4]));
        }

        java.util.Map<Long, Long> unread = new HashMap<>();
        if (userId != null) {
            List<Object[]> counts = em.createQuery(
                    "SELECT p.topic.id, COUNT(p) FROM ForumPost p WHERE p.topic.id IN :ids AND NOT EXISTS "
                            + "(SELECT v FROM ForumPostView v WHERE v.post = p AND v.user.id = :userId) "
                            + "GROUP BY p.topic.id", Object[].class)
                    .setParameter("ids", ids)
                    .setParameter("userId", userId)
                    .getResultList();
            for (Object[] row : counts) {
                unread.put((Long) row[0], (Long) row[1]);
            }
        }

        for (ForumTopic topic : topics) {
            ForumTopic.LatestPost post = latest.get(topic.getId());
            topic.setLatestPost(post);
            topic.setAllViewed(userId != null ? unread.getOrDefault(topic.getId(), 0L) == 0 : post == null);
        }
    }

    //CRUD operations for Forum Categories
    /*
        Brief addTopic
        - Checks if the Topic already exists
        - Check if passed Category exists
        - Create a formatted Time string from the local Server time
        - returns success or the error that accrued as a string
    */
    @Transactional
    @WithSpan
    public Response addTopic(ForumTopic topic, Long categoryId, Long userId){
        // Sanitize topic name (plain text — no HTML allowed in topic titles)
        topic.setTopic(htmlSanitizer.sanitizeTitle(topic.getTopic()));
        // Check if Category exists. Loaded uncached: the cached instances are detached and shared.
        ForumCategory forumCategory = categoryId != null ? em.find(ForumCategory.class, categoryId) : null;
        if(forumCategory == null) return Response.status(404).entity("Kategorie nicht gefunden").build();
        //Check if Topic exists in this category
        if(topicNameTaken(topic.getTopic(), categoryId, null)){
            Telemetry.event("forum.topic.duplicate", "forum.category.id", categoryId);
            return Response.status(409).entity("Thema exestiert bereits in dieser Kategorie").build();
        }

        topic.setCategory(forumCategory);
        //setCreationDate
        topic.setCreationDate(Time.currentTimeInMillis());
        //get corresponding user from db. Exit if not found
        User u = em.find(User.class, userId);
        if(u == null){
            Telemetry.warn("User not found", "user.id", userId);
            return Response.status(401).entity("User nicht gefunden").build();
        }

        if (!model.Users.Roles.hasRequiredRole(u.getRole(), forumCategory.getVisibility())) {
            return Response.status(403).entity("Du hast keine Berechtigung, in dieser Kategorie ein Thema zu erstellen.").build();
        }

        topic.setCreator(u);
        topic.setPostCount(0L);
        topic.setViews(0L);

        try {
            em.persist(topic);
        } catch (Exception e) {
             Telemetry.error("Adding topic failed", e, "forum.category.id", categoryId, "user.id", userId);
            return Response.status(500).entity("Fehler beim erstellen des neuen Tehmas").build();
        }
        ForumCounters.adjust(em, "ForumCategory", "topicCount", categoryId, 1);
        return Response.status(201).entity(topic).build();
    }
    /**
     * Updates the topic name. Only the creator or an Admin may update a topic.
     */
    @Transactional
    @WithSpan
    public Response updateTopic(ForumTopic forumTopic, Long userId){
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();

        ForumTopic forumTopicAusDB = em.find(ForumTopic.class, forumTopic.getId());
        if(forumTopicAusDB == null) return Response.status(404).entity("Thema nicht in der DB gefunden").build();

        User creator = forumTopicAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        // Sanitize topic name before merging
        String newName = htmlSanitizer.sanitizeTitle(forumTopic.getTopic());
        if (newName == null || newName.isBlank()) return Response.status(400).entity("Der Name des Themas darf nicht leer sein").build();
        if (forumTopicAusDB.getCategory() != null
                && topicNameTaken(newName, forumTopicAusDB.getCategory().getId(), forumTopicAusDB.getId())) {
            return Response.status(409).entity("Thema exestiert bereits in dieser Kategorie").build();
        }
        forumTopicAusDB.setTopic(newName);

        try{
            em.merge(forumTopicAusDB);
        }catch(Exception e){
            Telemetry.error("Updating topic failed", e, "forum.topic.id", forumTopic.getId(), "user.id", userId);
            return Response.status(500).entity("Fehler beim updaten der Antwort").build();
        }
        return Response.status(200).entity("Thema erfolgreich aktualisert").build();
    }
    /**
     * Deletes a topic and all its nested posts/answers. Only the creator or an Admin may delete a topic.
     */
    @Transactional
    @WithSpan
    public Response deleteTopic(ForumTopic forumTopic, Long userId){
        User user = em.find(User.class, userId);
        if(user == null) return Response.status(401).entity("User nicht gefunden").build();
        Long topicId = forumTopic.getId();
        ForumTopic forumTopicAusDB = em.find(ForumTopic.class, topicId);
        if(forumTopicAusDB == null) return Response.status(404).entity("Thema nicht in der DB gefunden").build();

        User creator = forumTopicAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole())) return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        try {
            Long categoryId = forumTopicAusDB.getCategory() != null ? forumTopicAusDB.getCategory().getId() : null;
            forumPostOrm.deleteAllPostsFromTopic(topicId);
            em.remove(forumTopicAusDB);
            ForumCounters.adjust(em, "ForumCategory", "topicCount", categoryId, -1);
        } catch (Exception e) {
            Telemetry.error("Deleting topic failed", e, "forum.topic.id", forumTopic.getId(), "user.id", userId);
            return Response.status(500).entity("Fehler beim Löschen des Topics").build();
        }

        return Response.status(200).entity("Thema erfolgreich gelöscht").build();
    }
    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    @WithSpan
    public String deleteAllTopicsFromCategory(Long categoryId){
        //Get all answers that will be affected to Update the affected user.
        List<ForumTopic> allTopics = getTopicsByCategory(categoryId);
        HashMap<User, Long> map = new HashMap<User,Long>();
        //Loop all answers to count the number of deleted answers per user.
        for (ForumTopic forumTopic : allTopics) {
           User u = forumTopic.getCreatorObj();
           if(map.containsKey(u))
           {
                map.put(u, map.get(u) + 1);
           }
           else{
               map.put(u, 1L);
           }
           forumPostOrm.deleteAllPostsFromTopic(forumTopic.getId());
        }
        try {
            em.createQuery("DELETE FROM ForumTopic ft WHERE ft.category.id = :val").setParameter("val", categoryId).executeUpdate();
        } catch (Exception e) {
            Telemetry.error("Deleting topics of category failed", e, "forum.category.id", categoryId);
            return "Fehler beim Löschen der Antworten";
        }

        return "Topics erfolgreich gelöscht:";
    }


}
