package resources.Forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import model.Forum.Polls.Polls;
import model.Users.Roles;
import orm.Forum.ForumPollOrm;
import helper.UserPrincipalResolver;
import tools.AuditLogger;
import java.util.List;
import java.util.logging.Logger;

@Path("/forum/poll")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ForumPollResource {

    private static final Logger log = Logger.getLogger(ForumPollResource.class.getName());

    @Inject
    ForumPollOrm forumPollOrm;

    @Inject
    UserPrincipalResolver userPrincipalResolver;

    @POST
    @Path("/create")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createPoll(@QueryParam("post") Long postId, Polls poll) {
        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Nicht angemeldet").build();
        }
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "CREATE", "Poll", "post=" + postId);
        return forumPollOrm.createPoll(postId, poll, userId);
    }

    @POST
    @Path("/vote")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    public Response vote(@QueryParam("poll") Long pollId, @QueryParam("option") List<Long> optionIds) {
        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Nicht angemeldet").build();
        }
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "VOTE", "Poll", pollId);
        return forumPollOrm.vote(pollId, optionIds, userId);
    }

    @GET
    @Path("/hasVoted")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    public Response hasVoted(@QueryParam("poll") Long pollId) {
        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Nicht angemeldet").build();
        }
        boolean voted = forumPollOrm.hasVoted(pollId, userId);
        return Response.ok(voted).build();
    }

    @GET
    @Path("/myVotes")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    public List<Long> getMyVotes(@QueryParam("poll") Long pollId) {
        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        return forumPollOrm.getVotedOptionIds(pollId, userId);
    }

    @GET
    @Path("/voters")
    @PermitAll
    public Response getVoters(@QueryParam("poll") Long pollId) {
        log.info("ForumPollResource/getVoters: " + pollId);
        if (pollId == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("poll parameter required").build();
        }
        // Returns voter names per option (empty for anonymous polls)
        jakarta.json.JsonArray result = forumPollOrm.getVoterNames(pollId);
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/delete")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    public Response deletePoll(@QueryParam("poll") Long pollId) {
        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Nicht angemeldet").build();
        }
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "DELETE", "Poll", pollId);
        return forumPollOrm.deletePoll(pollId, userId);
    }

    @POST
    @Path("/update")
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePoll(@QueryParam("poll") Long pollId, Polls poll) {
        Long userId = userPrincipalResolver.resolveUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Nicht angemeldet").build();
        }
        model.User user = userPrincipalResolver.resolveUser();
        AuditLogger.crud(log, user != null ? user.getUserName() : "unknown", userId,
                "UPDATE", "Poll", pollId);
        return forumPollOrm.updatePoll(pollId, poll, userId);
    }
}
