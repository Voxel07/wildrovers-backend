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
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;



@ApplicationScoped
public class ForumPostOrm {
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

    @WithSpan
    public List<ForumPost> getAllPosts() {
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c", ForumPost.class);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumPost> getPostsByUser(Long userId) {
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.creator.id = :val",
                ForumPost.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    /**
     * Paged post list for one reader. Only one filter is applied, in the order post, topic,
     * user, editor, title. Lists are newest first, as the topic table shows them.
     */
    @WithSpan
    public ForumPage<ForumPost> findPosts(Long postId, Long topicId, Long userId, Long editorId, String title,
            List<String> visibleRoles, Integer page, Integer size) {
        String filter = "";
        Object value = null;
        if (postId != null) { filter = " AND fp.id = :val"; value = postId; }
        else if (topicId != null) { filter = " AND t.id = :val"; value = topicId; }
        else if (userId != null) { filter = " AND fp.creator.id = :val"; value = userId; }
        else if (editorId != null) { filter = " AND fp.editor.id = :val"; value = editorId; }
        else if (title != null) { filter = " AND fp.title = :val"; value = title; }
        String where = " WHERE " + ForumPage.visibilityClause("c", visibleRoles) + filter;
        String order = " ORDER BY fp.creationDate DESC, fp.id DESC";

        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c" + where + order,
                ForumPost.class);
        TypedQuery<Long> count = em.createQuery(
                "SELECT COUNT(fp) FROM ForumPost fp LEFT JOIN fp.topic t LEFT JOIN t.category c" + where, Long.class);
        ForumPage.bindVisibility(query, visibleRoles);
        ForumPage.bindVisibility(count, visibleRoles);
        if (value != null) {
            query.setParameter("val", value);
            count.setParameter("val", value);
        }
        query.setFirstResult(ForumPage.firstResult(page, size));
        query.setMaxResults(ForumPage.pageSize(size));
        List<ForumPost> posts = query.getResultList();
        if (postId != null) {
            for (ForumPost post : posts) {
                org.hibernate.Hibernate.initialize(post.getPolls());
                for (model.Forum.Polls.Polls poll : post.getPolls()) {
                    org.hibernate.Hibernate.initialize(poll.getOptions());
                }
            }
        }
        return new ForumPage<>(posts, count.getSingleResult());
    }

    @WithSpan
    public List<ForumPost> getPostsById(Long postId) {
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.id = :val",
                ForumPost.class);
        query.setParameter("val", postId);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumPost> getPostsByEditor(Long userId) {
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.editor.id = :val",
                ForumPost.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumPost> getPostsByTopic(Long topicId) {
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.topic.id = :val",
                ForumPost.class);
        query.setParameter("val", topicId);
        return query.getResultList();
    }

    @WithSpan
    public List<ForumPost> getPostByTitel(String title) {
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp LEFT JOIN FETCH fp.topic t LEFT JOIN FETCH t.category c WHERE fp.title = :val",
                ForumPost.class);
        query.setParameter("val", title);
        return query.getResultList();
    }

    @WithSpan
    public ForumPost getLatestPost(Long topicId) {
        Telemetry.attr("forum.topic.id", topicId);
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
    @WithSpan
    public Response addPost(ForumPost forumPost, Long topicId, Long userId) {
        if (topicId == null)
            return Response.status(400).entity("Es muss ein Tehma angegeben werden").build();
        if (userId == null)
            return Response.status(401).entity("Es muss ein User angegebene werden").build();

        ForumTopic topic = em.find(ForumTopic.class, topicId);
        if (topic == null) {
            Telemetry.warn("Topic not found", "forum.topic.id", topicId);
            return Response.status(404).entity("Das angegebene Tehma existiert nicht").build();
        }
        String title = htmlSanitizer.sanitizeTitle(forumPost.getTitle());
        if (title == null || title.isBlank() || title.length() > 255) {
            return Response.status(400).entity("Der Titel muss 1 bis 255 Zeichen enthalten.").build();
        }
        // Compare exactly the sanitised title that will be stored.
        TypedQuery<ForumPost> query = em.createQuery(
                "SELECT fp FROM ForumPost fp WHERE fp.topic.id = :val AND fp.title = :val2", ForumPost.class);
        query.setParameter("val", topicId);
        query.setParameter("val2", title);
        if (!query.getResultList().isEmpty())
            return Response.status(409).entity("Ein Post mit diesem Titel exestiert bereit in diesem Thema").build();

        User user = em.find(User.class, userId);
        if (user == null)
            return Response.status(401).entity("Der angegebene Nutzer wurde nicht gefunden").build();

        if (!model.Users.Roles.hasRequiredRole(user.getRole(), topic.getCategory().getVisibility())) {
            return Response.status(403).entity("Du hast keine Berechtigung, in dieser Kategorie einen Beitrag zu erstellen.").build();
        }

        // Sanitize user-submitted content before persisting
        forumPost.setTitle(title);

        forumPost.setCreationDate(Time.currentTimeInMillis());

        forumPost.setTopic(topic);
        forumPost.setCreator(user);
        forumPost.setAnswerCount(0L);
        forumPost.setDislikes(0L);
        forumPost.setLikes(0L);
        forumPost.setViews(0L);

        try {
            em.persist(forumPost);
        } catch (Exception e) {
            Telemetry.error("Adding post failed", e, "forum.topic.id", topicId, "user.id", userId);
            return Response.status(500).entity("Fehler beim erstellen des Posts").build();
        }
        ForumCounters.adjust(em, "ForumTopic", "postCount", topicId, 1);

        // Extract base64 images from the ORIGINAL content (before sanitization strips
        // data: URIs), sanitize, and attach the author's editor uploads to this post.
        forumPost.setContent(imageStore.prepareContent(forumPost.getContent(), forumPost.getId(), userId));
        try {
            em.merge(forumPost);
        } catch (Exception e) {
            Telemetry.error("Image content update failed", e, "forum.topic.id", topicId);
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
    @WithSpan
    public Response updatePost(ForumPost forumPost, Long userId) {
        User user = em.find(User.class, userId);
        if (user == null)
            return Response.status(401).entity("User nicht gefunden").build();

        ForumPost forumPostAusDB = em.find(ForumPost.class, forumPost.getId());
        if (forumPostAusDB == null)
            return Response.status(404).entity("Post nicht gefunden").build();

        User creator = forumPostAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole()))
            return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole()))
            return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        // Extract base64 images from ORIGINAL content first, then sanitize
        if (forumPost.getTitle() != null) {
            String title = htmlSanitizer.sanitizeTitle(forumPost.getTitle());
            if (title.isBlank() || title.length() > 255) {
                return Response.status(400).entity("Der Titel muss 1 bis 255 Zeichen enthalten.").build();
            }
            if (em.createQuery("SELECT COUNT(p) FROM ForumPost p WHERE p.topic.id = :topic AND p.title = :title AND p.id <> :id", Long.class)
                    .setParameter("topic", forumPostAusDB.getTopic().getId())
                    .setParameter("title", title).setParameter("id", forumPostAusDB.getId()).getSingleResult() > 0) {
                return Response.status(409).entity("Ein Post mit diesem Titel existiert bereits in diesem Thema.").build();
            }
            forumPostAusDB.setTitle(title);
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
            Telemetry.error("Updating post failed", e, "forum.post.id", forumPost.getId(), "user.id", userId);
            return Response.status(500).entity("Fehler beim Aktualisieren des Posts").build();
        }
        return Response.status(200).entity("Post erfolgreich aktualisiert").build();
    }

    /**
     *
     * @param forumPost
     * @param userId
     * @return
     */
    // Deleting a post clears the forum link of its event.
    @io.quarkus.cache.CacheInvalidateAll.List({
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-in-range"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "upcoming-events"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-by-id"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @Transactional
    @WithSpan
    public Response deletePost(ForumPost forumPost, Long userId) {
        User user = em.find(User.class, userId);
        if (user == null)
            return Response.status(401).entity("User nicht gefunden").build();
        Long postId = forumPost.getId();
        ForumPost forumPostAusDB = em.find(ForumPost.class, postId);
        if (forumPostAusDB == null)
            return Response.status(404).entity("Post nicht gefunden").build();

        User creator = forumPostAusDB.getCreatorObj();
        if (creator == null && !model.Users.Roles.isModerator(user.getRole()))
            return Response.status(403).entity("Nur Moderatoren dürfen verwaiste Einträge bearbeiten").build();

        if (creator != null && !creator.getId().equals(userId) && !model.Users.Roles.isModerator(user.getRole()))
            return Response.status(403).entity("Nur der Ersteller oder Mods dürfen das").build();

        try {
            removePostCascade(forumPostAusDB);
        } catch (Exception e) {
            Telemetry.error("Deleting post failed", e, "forum.post.id", forumPost.getId(), "user.id", userId);
            return Response.status(500).entity("Fehler beim Löschen des Posts").build();
        }

        return Response.status(200).entity("Post erfolgreich gelöscht").build();
    }

    /**
     * Rewrites the generated announcement post of an event. No permission check: callers
     * (event updates) have checked them. Null values leave the field unchanged.
     */
    @Transactional
    public void updateGeneratedPost(Long postId, String title, String content, Long editorId) {
        ForumPost post = em.find(ForumPost.class, postId);
        if (post == null) return;
        if (title != null && !title.isBlank()) post.setTitle(htmlSanitizer.sanitizeTitle(title));
        if (content != null) post.setContent(imageStore.prepareContent(content, postId, editorId));
        User editor = editorId != null ? em.find(User.class, editorId) : null;
        if (editor != null) {
            post.setEditor(editor);
            post.setEditDate(Time.currentTimeInMillis());
        }
    }

    /**
     * Deletes a post with all dependent rows, without permission checks (event deletion).
     * Clears the persistence context, so callers must not use managed entities afterwards.
     */
    @Transactional
    public void deletePostById(Long postId) {
        ForumPost post = em.find(ForumPost.class, postId);
        if (post != null) {
            removePostCascade(post);
        }
    }

    /**
     * Deletes a post with everything that references it (answers, pictures, polls, votes, views)
     * and keeps the topic statistics in sync. Callers must have checked permissions.
     */
    private void removePostCascade(ForumPost post) {
        Long postId = post.getId();
        unlinkEvents(postId);
        // 1. Update stats (decrement post count on topic)
        if (post.getTopic() != null) {
            ForumCounters.adjust(em, "ForumTopic", "postCount", post.getTopic().getId(), -1);
        }

        // Decrement answer counts of all users who answered the post
        forumAnswerOrm.deleteAllAnswersFromTopic(postId);

        // Flush stats updates to the DB before clearing the session!
        em.flush();

        // 2. Run manual native SQL delete queries to delete all linked resources
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

    /** An event keeps existing when its discussion post is deleted, just without the link. */
    private void unlinkEvents(Long postId) {
        em.createQuery("UPDATE Event e SET e.forumPostId = NULL WHERE e.forumPostId = :postId")
                .setParameter("postId", postId)
                .executeUpdate();
    }

    /**
     * Moderation: deletes every post of a user including dependent data. A bulk JPQL
     * delete would violate the foreign keys of answers, votes, views, polls and pictures.
     */
    // Deleting a post clears the forum link of its event.
    @io.quarkus.cache.CacheInvalidateAll.List({
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-in-range"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "upcoming-events"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-by-id"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @Transactional
    @WithSpan
    public String deleteAllPostsFromUser(Long userId) {
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
    // Deleting a post clears the forum link of its event.
    @io.quarkus.cache.CacheInvalidateAll.List({
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-in-range"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "upcoming-events"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-by-id"),
        @io.quarkus.cache.CacheInvalidateAll(cacheName = "events-by-post-id")
    })
    @Transactional
    @WithSpan
    public String deleteAllPostsFromTopic(Long topicId) {
        // Get all posts to clean up associated data
        List<ForumPost> allPosts = getPostsByTopic(topicId);
        for (ForumPost forumPost : allPosts) {
            unlinkEvents(forumPost.getId());
            forumAnswerOrm.deleteAllAnswersFromTopic(forumPost.getId());

            // Delete votes and views
            em.createQuery("DELETE FROM ForumPostVote v WHERE v.post.id = :postId").setParameter("postId", forumPost.getId()).executeUpdate();
            em.createQuery("DELETE FROM ForumPostView v WHERE v.post.id = :postId").setParameter("postId", forumPost.getId()).executeUpdate();


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
            Telemetry.error("Deleting posts of topic failed", e, "forum.topic.id", topicId);
            return "Fehler beim Löschen der Antworten";
        }

        return "Posts erfolgreich gelöscht:";
    }

    /**
     * Increment like or dislike counter for a post with single-vote enforcement.
     */
    @Transactional
    @WithSpan
    public Response votePost(Long postId, String type, Long userId) {
        Telemetry.attrs("forum.post.id", postId, "forum.vote.type", type, "user.id", userId);
        // Row lock: concurrent votes on one post (e.g. a double click) run one after the other,
        // so the vote lookup and the counter changes below cannot interleave.
        ForumPost post = em.find(ForumPost.class, postId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
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
            Telemetry.error("Failed to save vote", e);
            return Response.status(500).entity("Fehler beim Speichern der Stimme").build();
        }

        jakarta.json.JsonObject result = jakarta.json.Json.createObjectBuilder()
                .add("likes", post.getLikes())
                .add("dislikes", post.getDislikes())
                .build();
        return Response.ok(result).build();
    }

    /**
     * Marks the post as read by the user. The first read per user also counts one view on the
     * post and its topic, so reloads and refetches do not inflate the counters. A concurrent
     * duplicate fails on the unique (user_id, post_id) key and rolls back as a whole.
     *
     * @return false if the post or user does not exist
     */
    @Transactional
    @WithSpan
    public boolean recordPostView(Long postId, Long userId) {
        Telemetry.attrs("forum.post.id", postId, "user.id", userId);
        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(pv) FROM ForumPostView pv WHERE pv.user.id = :userId AND pv.post.id = :postId",
            Long.class
        );
        query.setParameter("userId", userId);
        query.setParameter("postId", postId);
        User user = em.find(User.class, userId);
        ForumPost post = em.find(ForumPost.class, postId);
        if (user == null || post == null) return false;
        if (query.getSingleResult() > 0) return true;

        ForumPostView view = new ForumPostView();
        view.setUser(user);
        view.setPost(post);
        view.setViewedAt(Time.currentTimeInMillis());
        em.persist(view);
        em.flush();
        ForumCounters.adjust(em, "ForumPost", "views", postId, 1);
        if (post.getTopic() != null) {
            ForumCounters.adjust(em, "ForumTopic", "views", post.getTopic().getId(), 1);
        }
        return true;
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
