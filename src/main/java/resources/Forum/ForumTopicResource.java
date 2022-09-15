package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;


//Logging
import java.util.logging.Logger;

//Eigene Imports
import model.Forum.ForumTopic;
import orm.Forum.ForumTopicOrm;

@Path("/forum/topic")
// @RequestScoped
@ApplicationScoped
public class ForumTopicResource {
    private static final Logger log = Logger.getLogger(ForumTopicResource.class.getName());

    @Inject
    ForumTopicOrm forumTopicOrm;



    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumTopic> getTopics(  @QueryParam("topicId") Long topicId,
                                        @QueryParam("topic") String topic,
                                        @QueryParam("user")Long userId,
                                        @QueryParam("category")Long categoryId)
    {
         log.info("ForumResource/getTopics");
        if(topicId != null){
             log.info("ForumResource/getTopics/id");
            return forumTopicOrm.getTopicById(topicId);
        }
        else if(topic != null){
             log.info("ForumResource/getTopics/name");
            return forumTopicOrm.getTopicsByTopic(topic);
        }
        else if(userId != null){
            log.info("ForumResource/getTopics/user");
            return forumTopicOrm.getTopicsByUser(userId);
        }
        else if(categoryId != null){
            log.info("ForumResource/getTopics/category");
            return forumTopicOrm.getTopicsByCategory(categoryId);
        }
        else{
             log.info("ForumResource/getTopics/all");
            return forumTopicOrm.getAllTopics();
        }
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addTopic(ForumTopic ft, @QueryParam("category") Long categoryId, @QueryParam("creator") Long userId){
        log.info("ForumResource/addCategory");
        /*
        @ToDo
        -   Check permissions
        */
        return forumTopicOrm.addTopic(ft, categoryId, userId);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateTopic(ForumTopic ft,@QueryParam("user")Long userId){
        log.info("ForumResource/updateCategory");
        /*
        @ToDo
        -   Check permissions
        */
        return forumTopicOrm.updateTopic(ft, userId);
    }
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteTopic(ForumTopic ft, @QueryParam("user")Long userId){
        return forumTopicOrm.deleteTopic(ft,userId);
    }
}
