package resources.Forum;
//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;


//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import model.Forum.ForumPost;
import orm.Forum.ForumPostOrm;
import model.Forum.Pictures;
import tools.AuditLogger;

import model.Users.Roles;

@Path("/forum/post")
@ApplicationScoped
public class ForumPostResource {
    private static final Logger log = Logger.getLogger(ForumPostResource.class.getName());

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumPost> getPosts( @QueryParam("post") Long postId,
                            @QueryParam("title") String title,
                            @QueryParam("user")Long userId,
                            @QueryParam("topic")Long topicId,
                            @QueryParam("editor")Long editorId)
    {
        log.info("ForumResource/getTopics");
        List<ForumPost> posts;
        if(postId != null){
            log.info("ForumResource/getTopics/id");
            posts = forumPostOrm.getPostsById(postId);
            Long loggedInUserId = userPrincipalResolver.resolveUserId();
            if (loggedInUserId != null) {
                try {
                    forumPostOrm.recordPostView(postId, loggedInUserId);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to record post view", e);
                }
            }
        }
        else if(topicId != null){
            log.info("ForumResource/getTopics/name");
            posts = forumPostOrm.getPostsByTopic(topicId);
        }
        else if(userId != null){
            log.info("ForumResource/getPosts/user");
            posts = forumPostOrm.getPostsByUser(userId);
        }
        else if(editorId != null){
            log.info("ForumResource/getPosts/editor");
            posts = forumPostOrm.getPostsByEditor(editorId);
        }
        else if(title != null){
            log.info("ForumResource/getPosts/category");
            posts = forumPostOrm.getPostByTitel(title);
        }
        else{
            log.info("ForumResource/getPosts/all");
            posts = forumPostOrm.getAllPosts();
        }

        String userRole = Roles.VSISITOR;
        model.User user = userPrincipalResolver.resolveUser();
        if (user != null) {
            userRole = user.getRole();
        }
        final String finalRole = userRole;
        List<ForumPost> mutablePosts = new java.util.ArrayList<>(posts);
        mutablePosts.removeIf(fp -> {
            model.Forum.ForumTopic topic = fp.getTopic();
            if (topic == null) return false;
            model.Forum.ForumCategory cat = topic.getCategory();
            if (cat == null) return false;
            String vis = cat.getVisibility();
            if (vis == null || vis.isBlank()) {
                vis = Roles.VSISITOR;
            }
            return !Roles.hasRequiredRole(finalRole, vis);
        });

        Long loggedInUserId = userPrincipalResolver.resolveUserId();
        if (loggedInUserId != null && !mutablePosts.isEmpty()) {
            try {
                java.util.List<Long> postIds = new java.util.ArrayList<>();
                for (ForumPost fp : mutablePosts) {
                    if (fp.getId() != null) postIds.add(fp.getId());
                }
                java.util.Set<Long> viewedIds = forumPostOrm.getViewedPostIds(postIds, loggedInUserId);
                for (ForumPost fp : mutablePosts) {
                    if (viewedIds.contains(fp.getId())) {
                        fp.setViewed(true);
                    }
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to set viewed state on posts", e);
            }
        }
        return mutablePosts;
    }

    @GET
    @Path("/latest")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLatestPost(@QueryParam("topic") Long topicId){
        ForumPost fp = forumPostOrm.getLatestPost(topicId);
        if (fp == null || fp.getId() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String userRole = Roles.VSISITOR;
        model.User user = userPrincipalResolver.resolveUser();
        if (user != null) {
            userRole = user.getRole();
        }

        model.Forum.ForumTopic topic = fp.getTopic();
        if (topic != null) {
            model.Forum.ForumCategory cat = topic.getCategory();
            if (cat != null) {
                String vis = cat.getVisibility();
                if (vis == null || vis.isBlank()) {
                    vis = Roles.VSISITOR;
                }
                if (!Roles.hasRequiredRole(userRole, vis)) {
                    return Response.status(Response.Status.FORBIDDEN).entity("Keine Berechtigung").build();
                }
            }
        }
        return Response.ok(fp).build();
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
            model.User user = userPrincipalResolver.resolveUser();
            AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                    "CREATE", "Post", forumPost.getTitle());
            return forumPostOrm.addPost(forumPost, topicId, userId);
        }
    }
    @POST
    @Path("/img")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
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
            model.User user = userPrincipalResolver.resolveUser();
            AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                    "UPDATE", "Post", forumPost.getId());
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
            model.User user = userPrincipalResolver.resolveUser();
            AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                    "DELETE", "Post", forumPost.getId());
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
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "VOTE", "Post", postId, "type=" + type);
        return forumPostOrm.votePost(postId, type, userId);
    }
}

