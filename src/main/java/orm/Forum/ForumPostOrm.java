package orm.Forum;

//Datentypen
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;
//
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

import model.User;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import model.Forum.ForumPostView;
import orm.UserOrm;
import jakarta.ws.rs.core.Response;

//Time
import tools.Time;
import tools.HtmlSanitizer;
import tools.ForumImageStore;



@ApplicationScoped
public class ForumPostOrm {
    private static final Logger log = Logger.getLogger(ForumPostOrm.class.getName());
    @Inject
    EntityManager em;
    @Inject
    ForumTopicOrm forumTopicOrm;
    @Inject
    ForumAnswerOrm forumAnswerOrm;
    @Inject
    UserOrm userOrm;

    @Inject
    HtmlSanitizer htmlSanitizer;

    @Inject
    ForumImageStore imageStore;

    public List<ForumPost> getAllPosts() {
        log.info("ForumOrm/getPosts");
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c", ForumPost.class);
        return query.getResultList();
    }

    public List<ForumPost> getPostsByUser(Long userId) {
        log.info("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.creator.id = :val",
                ForumPost.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    @Transactional
    public List<ForumPost> getPostsById(Long postId) {
        log.info("ForumOrm/getPostsById");
        ForumPost post = em.find(ForumPost.class, postId);
        if (post != null) {
            post.setViews((post.getViews() != null ? post.getViews() : 0L) + 1);
            em.merge(post);
        }
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.id = :val",
                ForumPost.class);
        query.setParameter("val", postId);
        return query.getResultList();
    }

    public List<ForumPost> getPostsByEditor(Long userId) {
        log.info("ForumOrm/getPostsByEditor");
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.editor.id = :val",
                ForumPost.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    public List<ForumPost> getPostsByTopic(Long topicId) {
        log.info("ForumOrm/getPostsByTopic");
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.topic.id = :val",
                ForumPost.class);
        query.setParameter("val", topicId);
        return query.getResultList();
    }

    public List<ForumPost> getPostByTitel(String title) {
        log.info("ForumOrm/getPostByTitel");
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.title = :val",
                ForumPost.class);
        query.setParameter("val", title);
        return query.getResultList();
    }

    public ForumPost getLatestPost(Long topicId) {
        log.info("ForumPostOrm/getLatestPost " + topicId);
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.topic.id = :val ORDER BY fp.creationDate DESC",
                ForumPost.class);
        query.setParameter("val", topicId);
        query.setMaxResults(1);
        ForumPost fp = new ForumPost();
        try {
            fp = query.getSingleResult();
        } catch (Exception e) {
            return fp;
        }
        log.info("Creator:" + fp.getCreator());

        fp.setCreator(fp.getCreatorObj());

        return fp;
    }

    /** Title and audience of a post, read from the database for notifications. */
    public record NotificationInfo(String title, String requiredRole) { }

    public NotificationInfo notificationInfo(Long postId) {
        if (postId == null) return null;
        List<Object[]> rows = em.createQuery(
                "SELECT p.title, c.visibility FROM ForumPost p JOIN p.topic t JOIN t.category c WHERE p.id = :id",
                Object[].class)
                .setParameter("id", postId)
                .getResultList();
        if (rows.isEmpty()) return null;
        String visibility = (String) rows.get(0)[1];
        return new NotificationInfo((String) rows.get(0)[0],
                visibility == null || visibility.isBlank() ? model.Users.Roles.VSISITOR : visibility);
    }

    // Crud operations for ForumPosts
    /**
     * NOTE: addPost
     * - Checks if Post titel already exists in the same topic
     * -
     * 
     * @param forumPost Conaines all Content of the Post aka text and picutres
     * @param topicId
     * @param userId
     * @return
     */
    @Transactional
    public Response addPost(ForumPost forumPost, Long topicId, Long userId) {
        log.info("ForumPostOrm/addPost");
        if (topicId == null)
            return Response.status(401).entity("Es muss ein Tehma angegeben werden").build();
        if (userId == null)
            return Response.status(401).entity("Es muss ein User angegebene werden").build();

        List<ForumTopic> forumTopics = forumTopicOrm.getTopicById(topicId);
        if (forumTopics.isEmpty())
            return Response.status(401).entity("Topic nicht gefunden").build();
        ForumTopic topic = forumTopics.get(0);
        if (topic == null) {
            log.warning("TOPIC not found");
            return Response.status(401).entity("Das angegebene Tehma existiert nicht").build();
        }
        // Check if Post title exists in current Topic
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp WHERE fp.topic.id = :val AND fp.title = :val2", ForumPost.class);
        query.setParameter("val", topicId);
        query.setParameter("val2", forumPost.getTitle());
        if (!query.getResultList().isEmpty())
            return Response.status(401).entity("Ein Post mit diesem Titel exestiert bereit in diesem Thema").build();

        User user = em.find(User.class, userId);
        if (user == null)
            return Response.status(401).entity("Der angegebene Nutzer wurde nicht gefunden").build();

        if (!model.Users.Roles.hasRequiredRole(user.getRole(), topic.getCategory().getVisibility())) {
            return Response.status(403)
                    .entity("Du hast keine Berechtigung, in dieser Kategorie einen Beitrag zu erstellen.").build();
        }

        // Sanitize user-submitted content before persisting
        forumPost.setTitle(htmlSanitizer.sanitizeTitle(forumPost.getTitle()));

        forumPost.setCreationDate(Time.currentTimeInMillis());

        topic.incPostCount();
        forumPost.setTopic(topic);
        forumPost.setCreator(user);
        forumPost.setAnswerCount(0L);
        forumPost.setDislikes(0L);
        forumPost.setLikes(0L);
        forumPost.setViews(0L);

        try {
            em.persist(forumPost);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(401).entity("Fehler beim erstellen des Posts").build();
        }

        // Extract base64 images from the ORIGINAL content (before sanitization strips
        // data: URIs), sanitize, and attach the author's editor uploads to this post.
        forumPost.setContent(imageStore.prepareContent(forumPost.getContent(), forumPost.getId(), userId));
        try {
            em.merge(forumPost);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Image content update failed", e);
            // Non-fatal: post is saved, images may still be base64
        }

        return Response.status(201).entity(forumPost.getId()).build();
    }

    /**
     *
     * NOTE: updatePost
     * - Check permissions. Only creator/mods
     * - Check if new name exists already exists in the topic
     * 
     * @param forumPost
     * @param userId
     * @return
     */
    @Transactional
    public String updatePost(ForumPost forumPost, Long userId) {
        log.info("ForumPostOrm/updatePost");

        User user = em.find(User.class, userId);
        if (user == null)
            return "User nicht gefunden";

        ForumPost forumPostAusDB = em.find(ForumPost.class, forumPost.getId());
        if (forumPostAusDB == null)
            return "Antwort nicht in der DB gefunden";

        User creator = forumPostAusDB.getCreatorObj();
        if (creator == null && !user.getRole().equals("Admin"))
            return "Nur Admins dürfen verwaiste Einträge bearbeiten";

        if (creator != null && !creator.getId().equals(userId) && !user.getRole().equals("Admin"))
            return "Nur der Ersteller oder Mods dürfen das";

        // Extract base64 images from ORIGINAL content first, then sanitize
        if (forumPost.getTitle() != null && !forumPost.getTitle().isBlank()) {
            forumPostAusDB.setTitle(htmlSanitizer.sanitizeTitle(forumPost.getTitle()));
        }
        if (forumPost.getContent() != null) {
            forumPostAusDB.setContent(
                    imageStore.prepareContent(forumPost.getContent(), forumPostAusDB.getId(), userId));
        }

        forumPostAusDB.setEditDate(Time.currentTimeInMillis());
        forumPostAusDB.setEditor(user);

        try {
            em.merge(forumPostAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim updaten der Antwort";
        }
        return "Antwort erfolgreich aktualisert";
    }

    /**
     *
     * @param forumPost
     * @param userId
     * @return
     */
    @Transactional
    public String deletePost(ForumPost forumPost, Long userId) {
        log.info("ForumPostOrm/deletePost");

        User user = em.find(User.class, userId);
        if (user == null)
            return "User nicht gefunden";
        Long postId = forumPost.getId();
        ForumPost forumPostAusDB = em.find(ForumPost.class, postId);
        if (forumPostAusDB == null)
            return "Antwort nicht in der DB gefunden";

        User creator = forumPostAusDB.getCreatorObj();
        if (creator == null && !user.getRole().equals("Admin"))
            return "Nur Admins dürfen verwaiste Einträge bearbeiten";

        if (creator != null && !creator.getId().equals(userId) && !user.getRole().equals("Admin"))
            return "Nur der Ersteller oder Mods dürfen das";

        try {
            removePostCascade(forumPostAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen des Posts";
        }

        return "Post erfolgreich gelöscht";
    }

    /**
     * Deletes a post with everything that references it (answers, pictures, polls, votes, views)
     * and keeps the topic statistics in sync. Callers must have checked permissions.
     */
    private void removePostCascade(ForumPost post) {
        Long postId = post.getId();
        // 1. Update stats (decrement post count on topic)
        if (post.getTopic() != null) {
            post.getTopic().decPostCount();
            em.merge(post.getTopic());
        }

        // Decrement answer counts of all users who answered the post
        forumAnswerOrm.deleteAllAnswersFromTopic(postId);

        // Flush stats updates to the DB before clearing the session!
        em.flush();

        // 2. Run manual native SQL delete queries to delete all linked resources
        // A. Delete answer pictures
        em.createNativeQuery("DELETE FROM FORUM_PICTURE WHERE answer_id IN (SELECT id FROM FORUM_ANSWERS WHERE post_id = :postId)")
                .setParameter("postId", postId).executeUpdate();

        // B. Delete post pictures
        em.createNativeQuery("DELETE FROM FORUM_PICTURE WHERE post_id = :postId")
                .setParameter("postId", postId).executeUpdate();

        // C. Delete answers
        em.createNativeQuery("DELETE FROM FORUM_ANSWERS WHERE post_id = :postId")
                .setParameter("postId", postId).executeUpdate();

        // D. Delete poll option votes
        em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE option_id IN (SELECT id FROM FORUM_POLL_OPTIONS WHERE poll_id IN (SELECT id FROM FORUM_POLLS WHERE post_id = :postId))")
                .setParameter("postId", postId).executeUpdate();

        // E. Delete poll votes
        em.createNativeQuery("DELETE FROM FORUM_POLL_VOTES WHERE poll_id IN (SELECT id FROM FORUM_POLLS WHERE post_id = :postId)")
                .setParameter("postId", postId).executeUpdate();

        // F. Delete poll options
        em.createNativeQuery("DELETE FROM FORUM_POLL_OPTIONS WHERE poll_id IN (SELECT id FROM FORUM_POLLS WHERE post_id = :postId)")
                .setParameter("postId", postId).executeUpdate();

        // G. Delete polls
        em.createNativeQuery("DELETE FROM FORUM_POLLS WHERE post_id = :postId")
                .setParameter("postId", postId).executeUpdate();

        // H. Delete post votes
        em.createNativeQuery("DELETE FROM FORUM_POST_VOTES WHERE post_id = :postId")
                .setParameter("postId", postId).executeUpdate();

        // I. Delete post views
        em.createNativeQuery("DELETE FROM FORUM_POST_VIEWS WHERE post_id = :postId")
                .setParameter("postId", postId).executeUpdate();

        // J. Delete the post itself
        em.createNativeQuery("DELETE FROM FORUM_POSTS WHERE id = :postId")
                .setParameter("postId", postId).executeUpdate();

        // 3. Clear the persistence context so Hibernate forgets about the deleted objects in memory
        em.clear();
    }

    /**
     * Moderation: deletes every post of a user including dependent data. A bulk JPQL
     * delete would violate the foreign keys of answers, votes, views, polls and pictures.
     */
    @Transactional
    public String deleteAllPostsFromUser(Long userId) {
        log.info("ForumPostOrm/deleteAllPostsFromUser");

        List<Long> postIds = em.createQuery("SELECT fp.id FROM ForumPost fp WHERE fp.creator.id = :val", Long.class)
                .setParameter("val", userId)
                .getResultList();
        for (Long postId : postIds) {
            ForumPost post = em.find(ForumPost.class, postId);
            if (post != null) {
                removePostCascade(post);
            }
        }
        return postIds.size() + " Posts erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllPostsFromTopic(Long topicId) {
        log.info("ForumPostOrm/deleteAllPostsFromTopic");

        // Get all posts to clean up associated data
        List<ForumPost> allPosts = getPostsByTopic(topicId);
        for (ForumPost forumPost : allPosts) {
            forumAnswerOrm.deleteAllAnswersFromTopic(forumPost.getId());

            // Delete votes and views
            em.createQuery("DELETE FROM ForumPostVote v WHERE v.post.id = :postId").setParameter("postId", forumPost.getId()).executeUpdate();
            em.createQuery("DELETE FROM ForumPostView v WHERE v.post.id = :postId").setParameter("postId", forumPost.getId()).executeUpdate();

            // Delete pictures
            em.createQuery("DELETE FROM ForumPicture p WHERE p.post.id = :postId").setParameter("postId", forumPost.getId()).executeUpdate();

            // Delete polls
            List<model.Forum.Polls.Polls> polls = em.createQuery("SELECT p FROM Polls p WHERE p.post.id = :postId", model.Forum.Polls.Polls.class)
                    .setParameter("postId", forumPost.getId()).getResultList();
            for (model.Forum.Polls.Polls poll : polls) {
                em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE option_id IN (SELECT id FROM FORUM_POLL_OPTIONS WHERE poll_id = :pollId)")
                        .setParameter("pollId", poll.getId()).executeUpdate();
                em.createNativeQuery("DELETE FROM FORUM_POLL_VOTES WHERE poll_id = :pollId").setParameter("pollId", poll.getId()).executeUpdate();
                em.createQuery("DELETE FROM PollOptions o WHERE o.poll.id = :pollId").setParameter("pollId", poll.getId()).executeUpdate();
                em.remove(poll);
            }
        }
        try {
            em.createQuery("DELETE FROM ForumPost fp WHERE fp.topic.id = :val").setParameter("val", topicId)
                    .executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Antworten";
        }

        return "Posts erfolgreich gelöscht:";
    }

    /**
     * Increment like or dislike counter for a post with single-vote enforcement.
     */
    @Transactional
    public Response votePost(Long postId, String type, Long userId) {
        log.info("ForumPostOrm/votePost postId=" + postId + " type=" + type + " userId=" + userId);
        ForumPost post = em.find(ForumPost.class, postId);
        if (post == null) {
            return Response.status(404).entity("Post nicht gefunden").build();
        }
        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(401).entity("Nutzer nicht gefunden").build();
        }

        String targetType = type.toUpperCase(); // "LIKE" or "DISLIKE"
        if (!"LIKE".equals(targetType) && !"DISLIKE".equals(targetType)) {
            return Response.status(400).entity("Unbekannter vote-Typ: " + type).build();
        }

        if (post.getLikes() == null)
            post.setLikes(0L);
        if (post.getDislikes() == null)
            post.setDislikes(0L);

        TypedQuery<model.Forum.ForumPostVote> query = em.createQuery(
                "SELECT v FROM ForumPostVote v WHERE v.user.id = :userId AND v.post.id = :postId",
                model.Forum.ForumPostVote.class);
        query.setParameter("userId", userId);
        query.setParameter("postId", postId);

        List<model.Forum.ForumPostVote> votes = query.getResultList();
        if (votes.isEmpty()) {
            model.Forum.ForumPostVote newVote = new model.Forum.ForumPostVote();
            newVote.setUser(user);
            newVote.setPost(post);
            newVote.setVoteType(targetType);
            em.persist(newVote);

            if ("LIKE".equals(targetType)) {
                post.setLikes(post.getLikes() + 1);
            } else {
                post.setDislikes(post.getDislikes() + 1);
            }
        } else {
            model.Forum.ForumPostVote existingVote = votes.get(0);
            if (existingVote.getVoteType().equals(targetType)) {
                em.remove(existingVote);
                if ("LIKE".equals(targetType)) {
                    post.setLikes(Math.max(0, post.getLikes() - 1));
                } else {
                    post.setDislikes(Math.max(0, post.getDislikes() - 1));
                }
            } else {
                existingVote.setVoteType(targetType);
                em.merge(existingVote);

                if ("LIKE".equals(targetType)) {
                    post.setLikes(post.getLikes() + 1);
                    post.setDislikes(Math.max(0, post.getDislikes() - 1));
                } else {
                    post.setDislikes(post.getDislikes() + 1);
                    post.setLikes(Math.max(0, post.getLikes() - 1));
                }
            }
        }

        try {
            em.merge(post);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to save vote", e);
            return Response.status(500).entity("Fehler beim Speichern der Stimme").build();
        }

        jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                .add("likes", post.getLikes())
                .add("dislikes", post.getDislikes())
                .build();
        return Response.ok(result).build();
    }

    @Transactional
    public void recordPostView(Long postId, Long userId) {
        log.info("ForumPostOrm/recordPostView postId=" + postId + " userId=" + userId);
        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(pv) FROM ForumPostView pv WHERE pv.user.id = :userId AND pv.post.id = :postId",
            Long.class
        );
        query.setParameter("userId", userId);
        query.setParameter("postId", postId);
        Long count = query.getSingleResult();
        if (count == 0) {
            User user = em.find(User.class, userId);
            ForumPost post = em.find(ForumPost.class, postId);
            if (user != null && post != null) {
                ForumPostView view = new ForumPostView();
                view.setUser(user);
                view.setPost(post);
                view.setViewedAt(Time.currentTimeInMillis());
                em.persist(view);
            }
        }
    }

    public java.util.Set<Long> getViewedPostIds(List<Long> postIds, Long userId) {
        if (postIds == null || postIds.isEmpty() || userId == null) {
            return java.util.Collections.emptySet();
        }
        TypedQuery<Long> query = em.createQuery(
            "SELECT pv.post.id FROM ForumPostView pv WHERE pv.user.id = :userId AND pv.post.id IN :postIds",
            Long.class
        );
        query.setParameter("userId", userId);
        query.setParameter("postIds", postIds);
        return new java.util.HashSet<>(query.getResultList());
    }
}
