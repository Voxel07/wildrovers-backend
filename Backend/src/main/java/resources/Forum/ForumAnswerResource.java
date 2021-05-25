package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import model.Forum.ForumAnswer;
import orm.Forum.ForumAnswerOrm;

//Logging
import java.util.logging.Logger;

@Path("/forum/answer")
// @RequestScoped
@ApplicationScoped
public class ForumAnswerResource {
    private static final Logger log = Logger.getLogger(ForumAnswerResource.class.getName());

    @Inject
    ForumAnswerOrm forumAnswerOrm;

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
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addAnswer(ForumAnswer forumAnswer, @QueryParam("user") Long userId, @QueryParam("post") Long postId){
        log.info("ForumAnswerResource/addAnswer");
        if(postId == null) return "Es muss ein Tehma angegeben werden";
        if(userId == null) return "Es muss ein User angegebene werden";
        return forumAnswerOrm.addAnswer(forumAnswer, postId, userId);
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
    public String deleteAnswer(){
        log.info("ForumAnswerResource/deleteAnswer");
        return forumAnswerOrm.deleteAnswer();
    }
}
