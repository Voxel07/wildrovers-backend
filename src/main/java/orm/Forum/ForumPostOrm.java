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
//Zeit
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import model.User;
import model.Forum.ForumPicture;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import model.Forum.ForumPostView;
import model.Forum.Pictures;
import orm.UserOrm;
import jakarta.ws.rs.core.Response;

//Time
import tools.Time;
import tools.HtmlSanitizer;
import tools.ImageExtractor;

import org.eclipse.microprofile.config.inject.ConfigProperty;

//img
import java.awt.image.BufferedImage;
import org.apache.commons.io.FileUtils;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

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
    ImageExtractor imageExtractor;

    @ConfigProperty(name = "forum.images.upload-dir", defaultValue = "${user.home}/wildrovers-uploads/forum")
    String uploadDir;

    // Base URL for image references — configurable for production
    @ConfigProperty(name = "quarkus.http.host", defaultValue = "localhost")
    String serverHost;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int serverPort;

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

        user.getActivityForum().incPostCount();
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

        // Extract base64 images from ORIGINAL content first (before sanitization
        // strips data: URIs), THEN sanitize the result (now with safe HTTP URLs).
        String baseUrl = "http://" + serverHost + ":" + serverPort;
        String contentWithImages = imageExtractor.extractAndSaveImages(
                forumPost.getContent(), forumPost.getId(), baseUrl);
        forumPost.setContent(htmlSanitizer.sanitize(contentWithImages));
        try {
            em.merge(forumPost);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Image content update failed", e);
            // Non-fatal: post is saved, images may still be base64
        }

        return Response.status(201).entity(forumPost.getId()).build();
    }

    @Transactional
    public Response saveImages(Pictures pic, Long userId) {

        ForumPost post = getPostsById(pic.getPostId()).get(0);
        List<ForumPicture> pictures = new ArrayList<>();
        ForumPicture picture;
        // post.setPictures(pictures);

        int start = 0;
        int end = 0;
        String typeString; // data:image/png
        String type; // png
        List<String> types = new ArrayList<>();
        String base64Image;
        byte[] imageBytes;
        List<BufferedImage> imagList = new ArrayList<>();

        for (String elm : pic.getFiles()) {
            start = elm.indexOf("data:");
            end = elm.indexOf(";");
            typeString = elm.substring(start, end);

            if (typeString.contains("image")) {
                type = typeString.substring(11, typeString.length());
                types.add(type);
                base64Image = elm.split(",")[1];
                imageBytes = jakarta.xml.bind.DatatypeConverter.parseBase64Binary(base64Image);
                try {
                    imagList.add(ImageIO.read(new ByteArrayInputStream(imageBytes)));
                } catch (IOException e) {
                    return Response.status(500).entity("Fehler beim Konvertieren der Bilder").build();
                }
            }
        }

        // Ordnerstruktur erstellen
        File folder = new File("Forum/Posts/" + pic.getPostId() + "/images");
        if (!folder.exists()) {
            if (!folder.mkdirs())
                return Response.status(500).entity("Fehler beim erstellen der Ordnerstruktur").build();
        } else {
            try {
                FileUtils.cleanDirectory(folder);
            } catch (IOException e) {
                return Response.status(500).entity("Fehler beim Löschen des Ordnerinhalts").build();
            }
        }

        File outputfile;
        int i = 0;
        String path;

        for (BufferedImage img : imagList) {
            path = folder.getAbsolutePath() + "/img_" + i + "." + types.get(i);
            picture = new ForumPicture(path);
            post.getPictures().add(picture);
            picture.setPost(post);
            em.persist(picture);

            outputfile = new File(path);
            try {
                ImageIO.write(img, types.get(i), outputfile);
            } catch (IOException e) {
                return Response.status(500).entity("Fehler beim speichern der Bilder").build();
            }
            i++;
        }
        em.merge(post);
        return Response.ok("Beitragsbilder erfolgreich gespeichert").build();
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
        if (creator == null)
            return "creator nicht gesetzt";

        if (!creator.getId().equals(userId) && !user.getRole().equals("Admin"))
            return "Nur der Ersteller oder Mods dürfen das";

        // Extract base64 images from ORIGINAL content first, then sanitize
        String baseUrl = "http://" + serverHost + ":" + serverPort;
        String contentWithImages = imageExtractor.extractAndSaveImages(
                forumPost.getContent(), forumPost.getId(), baseUrl);
        forumPostAusDB.setContent(htmlSanitizer.sanitize(contentWithImages));

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
        if (creator == null)
            return "creator nicht gesetzt";

        if (!creator.getId().equals(userId) && !user.getRole().equals("Admin"))
            return "Nur der Ersteller oder Mods dürfen das";

        try {
            // Delete answers first
            forumAnswerOrm.deleteAllAnswersFromTopic(postId);

            // Delete votes and views
            em.createQuery("DELETE FROM ForumPostVote v WHERE v.post.id = :postId").setParameter("postId", postId).executeUpdate();
            em.createQuery("DELETE FROM ForumPostView v WHERE v.post.id = :postId").setParameter("postId", postId).executeUpdate();

            // Delete poll option votes and native poll tables
            if (forumPostAusDB.getPoll() != null) {
                model.Forum.Polls.Polls poll = forumPostAusDB.getPoll();
                em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE option_id IN (SELECT id FROM FORUM_POLL_OPTIONS WHERE poll_id = :pollId)")
                        .setParameter("pollId", poll.getId()).executeUpdate();
                em.createNativeQuery("DELETE FROM FORUM_POLL_VOTES WHERE poll_id = :pollId").setParameter("pollId", poll.getId()).executeUpdate();
                em.createQuery("DELETE FROM PollOptions o WHERE o.poll.id = :pollId").setParameter("pollId", poll.getId()).executeUpdate();
                em.remove(poll);
                forumPostAusDB.setPoll(null);
            }

            // Decrement counts
            creator.getActivityForum().decPostCount();
            if (forumPostAusDB.getTopic() != null) {
                forumPostAusDB.getTopic().decPostCount();
            }

            em.remove(forumPostAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen des Posts";
        }

        return "Post erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllPostsFromUser(Long userId) {
        log.info("ForumAnswerOrm/deleteAllPostsFromUser");

        try {
            em.createQuery("DELETE FROM ForumPost fp WHERE fp.creator.id = :val").setParameter("val", userId)
                    .executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Posts";
        }

        // Maybe set count to 0
        // User user = em.find(User.class, userId);
        // user.getActivityForum().setAnswerCount(0L);
        return "Posts erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllPostsFromTopic(Long topicId) {
        log.info("ForumPostOrm/deleteAllPostsFromTopic");

        // Get all answers that will be affected to Update the affected user.
        List<ForumPost> allPosts = getPostsByTopic(topicId);
        HashMap<User, Long> map = new HashMap<User, Long>();
        // Loop all answers to count the number of deleted answers per user.
        for (ForumPost forumPost : allPosts) {
            User u = forumPost.getCreatorObj();
            if (map.containsKey(u)) {
                map.put(u, map.get(u) + 1);
            } else {
                map.put(u, 1L);
            }
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

        for (Entry<User, Long> entry : map.entrySet()) {
            User k = entry.getKey();
            Long v = entry.getValue();
            k.getActivityForum().setPostCount(k.getActivityForum().getPostCount() - v);
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
