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
import orm.Forum.ForumPage;
import orm.Forum.ForumPostOrm;
import tools.AuditLogger;
import tools.NotificationService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import model.Users.Roles;

@Path("/forum/post")
@ApplicationScoped
public class ForumPostResource {
    private static final Logger log = Logger.getLogger(ForumPostResource.class.getName());

    @Inject
    ForumPostOrm forumPostOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    NotificationService notificationService;

    @Inject
    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getPosts( @QueryParam("post") Long postId,
                            @QueryParam("title") String title,
                            @QueryParam("user")Long userId,
                            @QueryParam("topic")Long topicId,
                            @QueryParam("editor")Long editorId,
                            @QueryParam("page") Integer page,
                            @QueryParam("size") Integer size)
    {
        log.info("ForumResource/getPosts");
        model.User user = userPrincipalResolver.resolveUser();
        String userRole = user != null ? user.getRole() : Roles.VSISITOR;
        ForumPage<ForumPost> result = forumPostOrm.findPosts(postId, topicId, userId, editorId, title,
                Roles.visibleTo(userRole), page, size);
        List<ForumPost> mutablePosts = new java.util.ArrayList<>(result.items());

        Long loggedInUserId = user != null ? user.getId() : null;
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
        return Response.ok(mutablePosts).header(ForumPage.TOTAL_HEADER, result.total()).build();
    }

    /**
     * Marks a post as read by the current user. Kept out of GET so that reloads and silent
     * refetches neither change state nor count as new views.
     */
    @POST
    @Path("/view")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    public Response recordView(@QueryParam("post") Long postId) {
        model.User user = userPrincipalResolver.resolveUser();
        if (user == null) return Response.status(401).build();
        if (postId == null) return Response.status(400).entity("Es muss ein Post angegeben werden").build();
        if (forumPostOrm.findPosts(postId, null, null, null, null, Roles.visibleTo(user.getRole()), 0, 1)
                .items().isEmpty()) {
            return Response.status(404).entity("Post nicht gefunden").build();
        }
        try {
            forumPostOrm.recordPostView(postId, user.getId());
        } catch (Exception e) {
            // A parallel request recorded the same view first (unique key); nothing to do.
            log.log(Level.FINE, "Post view already recorded", e);
        }
        return Response.noContent().build();
    }

    /** Forum notifications only reach subscribers who may read the post's category. */
    private void notifyForum(String action, Long postId, ForumPostOrm.NotificationInfo info, String url, Long actorId) {
        if (info == null) return;
        notificationService.record("FORUM", action, postId, info.title(), url, null, false, actorId,
                info.requiredRole());
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
            Response response = forumPostOrm.addPost(forumPost, topicId, userId);
            if (response.getStatus() == 201 && response.getEntity() instanceof Long postId) {
                notifyForum("CREATED", postId, forumPostOrm.notificationInfo(postId),
                        frontendUrl + "/Forum/Post/" + postId, userId);
            }
            return response;
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
            Response result = forumPostOrm.updatePost(forumPost, userId);
            if (result.getStatus() == 200) {
                notifyForum("UPDATED", forumPost.getId(), forumPostOrm.notificationInfo(forumPost.getId()),
                        frontendUrl + "/Forum/Post/" + forumPost.getId(), userId);
            }
            return result;
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
            // Title and audience must come from the stored post, before it is deleted.
            ForumPostOrm.NotificationInfo info = forumPostOrm.notificationInfo(forumPost.getId());
            Response result = forumPostOrm.deletePost(forumPost, userId);
            if (result.getStatus() == 200) {
                notifyForum("DELETED", forumPost.getId(), info, frontendUrl + "/Forum", userId);
            }
            return result;
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

