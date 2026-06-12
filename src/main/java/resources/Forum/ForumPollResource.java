package resources.Forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.RolesAllowed;
import model.Forum.Polls.Polls;
import model.Users.Roles;
import orm.Forum.ForumPollOrm;
import helper.UserPrincipalResolver;
import java.util.List;

@Path("/forum/poll")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ForumPollResource {

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
}
