package resources.Forum;

//Datentypen
import java.util.List;

import javax.annotation.security.RolesAllowed;
//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import io.vertx.core.http.HttpServerRequest;

//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;


import model.Forum.ForumAnswer;
import orm.Forum.ForumAnswerOrm;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
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
    @Context SecurityContext ctx;

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
    @RolesAllowed({Roles.FRESHMAN, Roles.MEMBER, Roles.ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addAnswer(ForumAnswer forumAnswer, @QueryParam("post") Long postId){
        log.info("ForumAnswerResource/addAnswer");
        Long userId = Long.parseLong(ctx.getUserPrincipal().getName());

        if(userId == null || forumAnswer == null)
        {
            return Response.status(401).entity("Fehleder oder falscher Parameter").build();
        }
        else
        {
            return forumAnswerOrm.addAnswer(forumAnswer, postId, userId);
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateAnswer(ForumAnswer forumAnswer, @QueryParam("user") Long userId){
        log.info("ForumAnswerResource/updateAnswer");
        if(userId == null) return "Es muss ein User angegebene werden";
        return forumAnswerOrm.updateAnswer(forumAnswer,userId);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteAnswer(ForumAnswer forumAnswer, @QueryParam("user") Long userId){
        log.info("ForumAnswerResource/deleteAnswer");
        log.info("Info: " + info.getQueryParameters() + "|" + info.getBaseUri() + "|" + info.getPath() + "|" + info.getAbsolutePath());
        log.info("Request: " + request.response() + "|" + request.host()+ "|" + request.cookieCount()+ "|" + request.remoteAddress()+ "|" + request.localAddress());
        if(userId == null) return "Es muss ein User angegebene werden";
        return forumAnswerOrm.deleteAnswer(forumAnswer,userId);
    }
}
