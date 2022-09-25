package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.annotation.security.RolesAllowed;
import model.Users.Roles;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Context;


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
    @RolesAllowed({Roles.FRESHMAN, Roles.MEMBER, Roles.ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addTopic(ForumTopic forumTopic, @QueryParam("category") Long categoryId, @Context SecurityContext ctx){
        log.info("ForumResource/addCategory");
        Long userId = Long.parseLong(ctx.getUserPrincipal().getName());
        log.info(ctx.getUserPrincipal().getName());

        if(userId == null || forumTopic.getTopic() == null)
        {
            return Response.status(401).entity("Fehleder oder falscher Parameter").build();
        }
        else
        {
            return forumTopicOrm.addTopic(forumTopic, categoryId, userId);
        }
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
