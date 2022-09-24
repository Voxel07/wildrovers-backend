package resources.Forum;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;
//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;


//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

//Eigene Imports
import model.Forum.ForumPost;
import orm.Forum.ForumPostOrm;
import model.Forum.Pictures;

//Security
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Context;
import javax.annotation.security.RolesAllowed;
import model.Users.Roles;

@Path("/forum/post")
// @RequestScoped
@ApplicationScoped
public class ForumPostResource {
    private static final Logger log = Logger.getLogger(ForumPostResource.class.getName());

    @Inject
    ForumPostOrm forumPostOrm;

    @Context
    SecurityContext ctx;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumPost> getPosts( @QueryParam("post") Long postId,
                            @QueryParam("title") String title,
                            @QueryParam("user")Long userId,
                            @QueryParam("topic")Long topicId,
                            @QueryParam("editor")Long editorId)
    {
        log.info("ForumResource/getTopics");
        if(postId != null){
            log.info("ForumResource/getTopics/id");
            return forumPostOrm.getPostsById(postId);
        }
        else if(topicId != null){
            log.info("ForumResource/getTopics/name");
            return forumPostOrm.getPostsByTopic(topicId);
        }
        else if(userId != null){
            log.info("ForumResource/getPosts/user");
            return forumPostOrm.getPostsByUser(userId);
        }
        else if(editorId != null){
            log.info("ForumResource/getPosts/editor");
            return forumPostOrm.getPostsByEditor(editorId);
        }
        else if(title != null){
            log.info("ForumResource/getPosts/category");
            return forumPostOrm.getPostByTitel(title);
        }
        else{
                log.info("ForumResource/getPosts/all");
            return forumPostOrm.getAllPosts();
        }
    }

    @GET
    @Path("/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public ForumPost getLatestPost(@QueryParam("topic") Long topicId){
        return forumPostOrm.getLatestPost(topicId);
    }


    @PUT
    @RolesAllowed({Roles.FRESHMAN, Roles.MEMBER, Roles.ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addPost(ForumPost forumPost,@QueryParam("topic")Long topicId){
        log.info("ForumPostResource/addPost");
        Long userId = Long.parseLong(ctx.getUserPrincipal().getName());

        if(userId == null || forumPost == null)
        {
            return Response.status(401).entity("Fehleder oder falscher Parameter").build();
        }
        else
        {
            return forumPostOrm.addPost(forumPost, topicId, userId);
        }
    }
    @POST
    @Path("/img")
    @RolesAllowed({Roles.FRESHMAN, Roles.MEMBER, Roles.ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    // @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response saveImages(Pictures pic){
        Long userId = Long.parseLong(ctx.getUserPrincipal().getName());
            log.info("ID:"+ pic.getPostId());
        if(userId == null || pic.getFiles().isEmpty())
        {
            return Response.status(401).entity("Fehleder oder falscher Parameter").build();
        }
        else
        {
            return forumPostOrm.saveImages(pic, userId);
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updatePost(ForumPost forumPost, @QueryParam("user") Long userId){
        log.info("ForumPostResource/updatePost");
        /**
         * TODO:
         * -    Check permissions
         */
        return forumPostOrm.updatePost(forumPost, userId);
    }
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deletePost(ForumPost forumPost, @QueryParam("user") Long userId){
        log.info("ForumPostResource/deletePost");
        /**
         * TODO:
         * -    Check permissions
         */
        return forumPostOrm.deletePost(forumPost, userId);
    }
}
