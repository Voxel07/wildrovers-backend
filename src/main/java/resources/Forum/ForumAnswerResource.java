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
import orm.Forum.ForumPage;
import tools.AuditLogger;

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
    public Response getAnswers(@QueryParam("answer") Long answerId,
                                        @QueryParam("user")Long userId,
                                        @QueryParam("post")Long postId,
                                        @QueryParam("editor")Long editorId,
                                        @QueryParam("page") Integer page,
                                        @QueryParam("size") Integer size)
    {
        log.info("ForumAnswerResource/getAnswers");
        model.User user = userPrincipalResolver.resolveUser();
        String userRole = user != null ? user.getRole() : Roles.VSISITOR;
        ForumPage<ForumAnswer> result = forumAnswerOrm.findAnswers(answerId, postId, userId, editorId,
                Roles.visibleTo(userRole), page, size);
        return Response.ok(result.items()).header(ForumPage.TOTAL_HEADER, result.total()).build();
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
            model.User user = userPrincipalResolver.resolveUser();
            AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                    "CREATE", "Answer", postId);
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
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "UPDATE", "Answer", forumAnswer.getId());
        return forumAnswerOrm.updateAnswer(forumAnswer, userId);
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
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "DELETE", "Answer", forumAnswer.getId());
        return forumAnswerOrm.deleteAnswer(forumAnswer, userId);
    }
}
