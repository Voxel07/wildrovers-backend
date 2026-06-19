package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;


import model.Forum.ForumAnswer;
import orm.Forum.ForumAnswerOrm;

import jakarta.ws.rs.core.Response;
import model.Users.Roles;

//Logging
import java.util.logging.Logger;

@Path("/forum/answer")
@ApplicationScoped
public class ForumAnswerResource{
    private static final Logger log = Logger.getLogger(ForumAnswerResource.class.getName());

    @Inject
    ForumAnswerOrm forumAnswerOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumAnswer> getAnswers(@QueryParam("answer") Long answerId,
                                        @QueryParam("user")Long userId,
                                        @QueryParam("post")Long postId,
                                        @QueryParam("editor")Long editorId)
    {
        log.info("ForumAnswerResource/getAnswers");
        List<ForumAnswer> answers;
        if(answerId != null){
            log.info("ForumAnswerResource/getAnswers/id");
            answers = forumAnswerOrm.getAnswersById(answerId);
        }
        else if(postId != null){
            log.info("ForumAnswerResource/getAnswers/name");
            answers = forumAnswerOrm.getAnswersByPost(postId);
        }
        else if(userId != null){
            log.info("ForumAnswerResource/getAnswers/user");
            answers = forumAnswerOrm.getAnswersByUser(userId);
        }
        else if(editorId != null){
            log.info("ForumAnswerResource/getAnswers/editor");
            answers = forumAnswerOrm.getAnswersByEditor(editorId);
        }
        else{
            log.info("ForumAnswerResource/getAnswers/all");
            answers = forumAnswerOrm.getAllAnswers();
        }

        String userRole = Roles.VSISITOR;
        model.User user = userPrincipalResolver.resolveUser();
        if (user != null) {
            userRole = user.getRole();
        }
        final String finalRole = userRole;
        List<ForumAnswer> mutableAnswers = new java.util.ArrayList<>(answers);
        mutableAnswers.removeIf(fa -> {
            model.Forum.ForumPost post = fa.getPost();
            if (post == null) return false;
            model.Forum.ForumTopic topic = post.getTopic();
            if (topic == null) return false;
            model.Forum.ForumCategory cat = topic.getCategory();
            if (cat == null) return false;
            String vis = cat.getVisibility();
            if (vis == null || vis.isBlank()) {
                vis = Roles.VSISITOR;
            }
            return !Roles.hasRequiredRole(finalRole, vis);
        });
        return mutableAnswers;
    }
    @PUT
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addAnswer(ForumAnswer forumAnswer, @QueryParam("post") Long postId){
        log.info("ForumAnswerResource/addAnswer");
        Long userId = userPrincipalResolver.resolveUserId();

        if(userId == null || forumAnswer == null)
        {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        else
        {
            return forumAnswerOrm.addAnswer(forumAnswer, postId, userId);
        }
    }

    @POST
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateAnswer(ForumAnswer forumAnswer){
        log.info("ForumAnswerResource/updateAnswer");
        Long userId = userPrincipalResolver.resolveUserId();
        if(userId == null || forumAnswer == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        String result = forumAnswerOrm.updateAnswer(forumAnswer,userId);
        return Response.ok(result).build();
    }

    @DELETE
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteAnswer(ForumAnswer forumAnswer){
        log.info("ForumAnswerResource/deleteAnswer");
        Long userId = userPrincipalResolver.resolveUserId();
        if(userId == null || forumAnswer == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        String result = forumAnswerOrm.deleteAnswer(forumAnswer,userId);
        return Response.ok(result).build();
    }
}
