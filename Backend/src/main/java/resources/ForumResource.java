package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.swing.text.AttributeSet.ColorAttribute;
import javax.ws.rs.InternalServerErrorException;
//HTTP Requests
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

//Eigene Imports
import model.User;
import model.Forum.ForumCategory;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import orm.UserOrm;
import orm.Forum.ForumCategoryOrm;
import orm.Forum.ForumTopicOrm;

import javax.ws.rs.QueryParam;

//Emailzeug
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.reactive.ReactiveMailer;

//Sicherheits Zeug
import javax.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.jwt.JsonWebToken;
import java.security.Principal;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

//Logging
import java.util.logging.Logger;

@Path("/forum")
// @RequestScoped
@ApplicationScoped
public class ForumResource {
    private static final Logger log = Logger.getLogger(ForumResource.class.getName());
    
    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @Inject
    ForumTopicOrm forumTopicOrm;

    @GET
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumCategory> getCategorys(@QueryParam("categoryId") Long categorId,@QueryParam("category") String category)
    {
         log.info("ForumResource/getCategorys");
        if(categorId != null){
             log.info("ForumResource/getCategorys/id");
            return forumCategoryOrm.getCategoriesById(categorId);
        }
        else if(category != null){
             log.info("ForumResource/getCategorys/name");
            return forumCategoryOrm.getCategoriesByName(category);
        }
        else{
             log.info("ForumResource/getCategorys/all");
            return forumCategoryOrm.getAllCategories();
        }
    }

    @PUT
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addCategory(ForumCategory fc, @QueryParam("creator") Long userId){
         log.info("ForumResource/addCategory");
        /*
        ToDo
        -   Check permissions
        */
        return forumCategoryOrm.addCategory(fc,userId);
    }
    @POST
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateCategory(ForumCategory fc){
        log.info("ForumResource/updateCategory");
        /*
        @ToDo
        -   Check permissions
        */
        return forumCategoryOrm.updateCategory(fc);
    }

    @DELETE
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteCategory(ForumCategory fc){
        log.info("ForumResource/deleteCategory");
        /*
        @ToDo
        -   Check permissions
        */
        return forumCategoryOrm.removeCategory(fc);
    }

    @GET
    @Path("topic")
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
    @Path("topic")
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
    @Path("topic")
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
    @Path("topic")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteTopic(ForumTopic ft){
        return forumTopicOrm.deleteTopic(ft);
    }
}
