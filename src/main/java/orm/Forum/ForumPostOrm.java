package orm.Forum;

//Datentypen
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;
//
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;


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
import model.Forum.Pictures;
import orm.UserOrm;
import javax.ws.rs.core.Response;

//Time
import tools.Time;

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


    public List<ForumPost>getAllPosts(){
        log.info("ForumOrm/getPosts");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp", ForumPost.class);
        return query.getResultList();
    }
    public List<ForumPost>getPostsByUser(Long userId){
        log.info("ForumOrm/getPostsByUser");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE user_id =: val", ForumPost.class);
        query.setParameter("val",userId);
        return query.getResultList();
    }
    public List<ForumPost>getPostsById(Long postId){
        log.info("ForumOrm/getPostsById");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE id =: val", ForumPost.class);
        query.setParameter("val",postId);
        return query.getResultList();
   }
    public List<ForumPost>getPostsByEditor(Long userId){
        log.info("ForumOrm/getPostsByEditor");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE  editor_id =: val", ForumPost.class);
        query.setParameter("val",userId);
        return query.getResultList();
   }
    public List<ForumPost>getPostsByTopic(Long topicId){
        log.info("ForumOrm/getPostsByTopic");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE topic_id =: val", ForumPost.class);
        query.setParameter("val",topicId);
        return query.getResultList();
    }
    public List<ForumPost>getPostByTitel(String title){
        log.info("ForumOrm/getPostByTitel");
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE title =: val", ForumPost.class);
        query.setParameter("val",title);
        return query.getResultList();
    }
    public ForumPost getLatestPost(Long topicId){
        log.info("ForumPostOrm/getLatestPost "+ topicId);
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE topic_id =: val ORDER BY creationDate DESC", ForumPost.class);
        query.setParameter("val", topicId);
        query.setMaxResults(1);
        ForumPost fp = new ForumPost();
        try {
            fp = query.getSingleResult();
        } catch (Exception e) {
           return fp;
        }
        log.info("Creator:" + fp.getCreator());

        fp.setCreator(fp.getCreator());

        return fp;
    }

    //Crud operations for ForumPosts
    /**
     *  NOTE: addPost
        -   Checks if Post titel already exists in the same topic
        -
     * @param forumPost Conaines all Content of the Post aka text and picutres
     * @param topicId
     * @param userId
     * @return
     */
    @Transactional
    public Response addPost(ForumPost forumPost,Long topicId,Long userId){
        log.info("ForumPostOrm/addPost");
        if(topicId == null) return Response.status(401).entity("Es muss ein Tehma angegeben werden").build();
        if(userId == null) return Response.status(401).entity("Es muss ein User angegebene werden").build();

        List<ForumTopic> forumTopics = forumTopicOrm.getTopicById(topicId);
        if(forumTopics.isEmpty()) return Response.status(401).entity("Topic nicht gefunden").build();
        ForumTopic topic = forumTopics.get(0);
        if(topic == null){
            log.warning("TOPIC not found");
            return Response.status(401).entity("Das angegebene Tehma existiert nicht").build();
        }
        //Check if Post title exists in current Topic
        TypedQuery<ForumPost> query = em.createQuery("SELECT fp FROM ForumPost fp WHERE topic_id =: val AND title =: val2",ForumPost.class);
        query.setParameter("val", topicId);
        query.setParameter("val2", forumPost.getTitle());
        if(!query.getResultList().isEmpty()) return Response.status(401).entity("Ein Post mit diesem Titel exestiert bereit in diesem Thema").build();

        User user = em.find(User.class,userId);
        if(user == null) return Response.status(401).entity("Der angegebene Nutzer wurde nicht gefunden").build();

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
            return  Response.status(401).entity("Fehler beim erstellen des Posts").build();
        }
        return  Response.status(201).entity(forumPost.getId()).build();
    }

    @Transactional
    public Response saveImages(Pictures pic, Long userId){

        ForumPost post = getPostsById(pic.getPostId()).get(0);
        List <ForumPicture> pictures = new ArrayList<>();
        ForumPicture picture;
        // post.setPictures(pictures);

        int start = 0;
        int end = 0;
        String typeString; //data:image/png
        String type; //png
        List <String> types = new ArrayList<>();
        String base64Image;
        byte[] imageBytes;
        List<BufferedImage> imagList = new ArrayList<>();

        for (String elm : pic.getFiles())
        {
            start = elm.indexOf("data:");
            end = elm.indexOf(";");
            typeString = elm.substring(start, end);

            if(typeString.contains("image")){
                type = typeString.substring(11, typeString.length());
                types.add(type);
                base64Image = elm.split(",")[1];
                imageBytes = javax.xml.bind.DatatypeConverter.parseBase64Binary(base64Image);
                try {
                    imagList.add(ImageIO.read(new ByteArrayInputStream(imageBytes)));
                } catch (IOException e) {
                    return Response.status(500).entity("Fehler beim Konvertieren der Bilder").build();
                }
            }
        }

        //Ordnerstruktur erstellen
        File folder = new File("Forum/Posts/"+pic.getPostId()+"/images");
        if(!folder.exists()){
            if(!folder.mkdirs()) return Response.status(500).entity("Fehler beim erstellen der Ordnerstruktur").build();
        }
        else{
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
            path = folder.getAbsolutePath()+"/img_"+i+"."+types.get(i);
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
     * -    Check permissions. Only creator/mods
     * -    Check if new name exists already exists in the topic
     * @param forumPost
     * @param userId
     * @return
     */
    @Transactional
    public String updatePost(ForumPost forumPost, Long userId){
        log.info("ForumPostOrm/updatePost");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";

        ForumPost forumPostAusDB = em.find(ForumPost.class, forumPost.getId());
        if(forumPostAusDB == null) return "Antwort nicht in der DB gefunden";

        User creator = forumPostAusDB.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        forumPostAusDB.setContent(forumPost.getContent());

        forumPostAusDB.setEditDate(Time.currentTimeInMillis());
        forumPostAusDB.setEditor(user);

        try{
            em.merge(forumPostAusDB);
        }catch(Exception e){
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
    public String deletePost(ForumPost forumPost, Long userId){
        log.info("ForumPostOrm/deletePost");

        User user = em.find(User.class, userId);
        if(user == null) return "User nicht gefunden";
        Long postId = forumPost.getId();
        ForumPost forumPostAusDB = em.find(ForumPost.class, postId);
        if(forumPostAusDB == null) return "Antwort nicht in der DB gefunden";

        User creator = forumPostAusDB.getCreator();
        if (creator == null) return "creator nicht gesetzt";

        if(!creator.getId().equals(userId) && !user.getRole().equals("Admin")) return "Nur der Ersteller oder Mods dürfen das";

        try {
            em.remove(forumPostAusDB);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen des Posts";
        }

        creator.getActivityForum().decPostCount();
        forumPostAusDB.getTopic().decPostCount();
        forumAnswerOrm.deleteAllAnswersFromTopic(postId);

        return "Post erfolgreich gelöscht";
    }
    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllPostsFromUser(Long userId){
        log.info("ForumAnswerOrm/deleteAllPostsFromUser");

        try {
            em.createQuery("DELETE fa FROM ForumPosts fa WHERE user_id =: val").setParameter("val", userId).executeUpdate();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return "Fehler beim Löschen der Posts";
        }

        //Maybe set count to 0
        // User user = em.find(User.class, userId);
        // user.getActivityForum().setAnswerCount(0L);
        return "Posts erfolgreich gelöscht";
    }

    /**
     * No checks, because this function does not have a public endpoint
     * gets Called when a Topic is deleted so no need to update answer count
     */
    @Transactional
    public String deleteAllPostsFromTopic(Long topicId){
        log.info("ForumPostOrm/deleteAllPostsFromTopic");

        //Get all answers that will be affected to Update the affected user.
        List<ForumPost> allPosts = getPostsByTopic(topicId);
        HashMap<User, Long> map = new HashMap<User,Long>();
        //Loop all answers to count the number of deleted answers per user.
        for (ForumPost forumPost : allPosts) {
           User u = forumPost.getCreator();
           if(map.containsKey(u))
           {
                map.put(u, map.get(u) + 1);
           }
           else{
               map.put(u, 1L);
           }
           forumAnswerOrm.deleteAllAnswersFromTopic(forumPost.getId());
        }
        try {
            em.createQuery("DELETE fp FROM ForumPost fp WHERE topic_id =: val").setParameter("val", topicId).executeUpdate();
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
}
