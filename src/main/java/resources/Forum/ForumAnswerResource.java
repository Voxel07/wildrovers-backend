package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import io.vertx.core.http.HttpServerRequest;
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
import jakarta.ws.rs.core.SecurityContext;
import model.Users.Roles;

//Logging
import java.util.logging.Logger;

@Path("/forum/answer")
@ApplicationScoped
public class ForumAnswerResource{
    private static final Logger log = Logger.getLogger(ForumAnswerResource.class.getName());

    @Inject
    ForumAnswerOrm forumAnswerOrm;

    @Context
    UriInfo info;

    @Context
    HttpServerRequest request;
    
    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumAnswer> getAnswers(@QueryParam("answer") Long answerId,
                                        @QueryParam("user")Long userId,
                                        @QueryParam("post")Long postId,
                                        @QueryParam("editor")Long editorId)
        {
        log.info("ForumAnswerResource/getAnswers");
        if(answerId != null){
            log.info("ForumAnswerResource/getAnswers/id");
            return forumAnswerOrm.getAnswersById(answerId);
        }
        else if(postId != null){
            log.info("ForumAnswerResource/getAnswers/name");
            return forumAnswerOrm.getAnswersByPost(postId);
        }
        else if(userId != null){
            log.info("ForumAnswerResource/getAnswers/user");
            return forumAnswerOrm.getAnswersByUser(userId);
        }
        else if(editorId != null){
            log.info("ForumAnswerResource/getAnswers/editor");
            return forumAnswerOrm.getAnswersByEditor(editorId);
        }
        else{
            log.info("ForumAnswerResource/getAnswers/all");
            return forumAnswerOrm.getAllAnswers();
        }

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
        log.info("Info: " + info.getQueryParameters() + "|" + info.getBaseUri() + "|" + info.getPath() + "|" + info.getAbsolutePath());
        log.info("Request: " + request.response() + "|" + request.host()+ "|" + request.cookieCount()+ "|" + request.remoteAddress()+ "|" + request.localAddress());
        Long userId = userPrincipalResolver.resolveUserId();
        if(userId == null || forumAnswer == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        String result = forumAnswerOrm.deleteAnswer(forumAnswer,userId);
        return Response.ok(result).build();
    }
}
