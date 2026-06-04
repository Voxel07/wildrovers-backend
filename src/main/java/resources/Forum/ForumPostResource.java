package resources.Forum;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.json.JSONException;
//HTTP Requests
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;


//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

import jakarta.annotation.security.RolesAllowed;
import model.Forum.ForumPost;
import orm.Forum.ForumPostOrm;
import model.Forum.Pictures;

//Security
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.Context;
import model.Users.Roles;

@Path("/forum/post")
// @RequestScoped
@ApplicationScoped
public class ForumPostResource {
    private static final Logger log = Logger.getLogger(ForumPostResource.class.getName());

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

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
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addPost(ForumPost forumPost,@QueryParam("topic")Long topicId){
        log.info("ForumPostResource/addPost");
        Long userId = userPrincipalResolver.resolveUserId();

        if(userId == null || forumPost == null)
        {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        else
        {
            return forumPostOrm.addPost(forumPost, topicId, userId);
        }
    }
    @POST
    @Path("/img")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    // @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response saveImages(Pictures pic){
        Long userId = userPrincipalResolver.resolveUserId();
        log.info("ID:"+ pic.getPostId());
        if(userId == null || pic.getFiles().isEmpty())
        {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        else
        {
            return forumPostOrm.saveImages(pic, userId);
        }
    }

    @POST
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePost(ForumPost forumPost){
        log.info("ForumPostResource/updatePost");
        Long userId = userPrincipalResolver.resolveUserId();

        if (userId == null || forumPost == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        } else {
            String result = forumPostOrm.updatePost(forumPost, userId);
            return Response.ok(result).build();
        }
    }
    @DELETE
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deletePost(ForumPost forumPost){
        log.info("ForumPostResource/deletePost");
        Long userId = userPrincipalResolver.resolveUserId();

        if (userId == null || forumPost == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        } else {
            String result = forumPostOrm.deletePost(forumPost, userId);
            return Response.ok(result).build();
        }
    }

    @POST
    @Path("/vote")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response votePost(@QueryParam("post") Long postId, @QueryParam("type") String type) {
        log.info("ForumPostResource/votePost");
        Long userId = userPrincipalResolver.resolveUserId();

        if (userId == null || postId == null || type == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        return forumPostOrm.votePost(postId, type);
    }
}

