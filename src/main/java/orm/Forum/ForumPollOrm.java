package orm.Forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import model.Forum.ForumPost;
import model.Forum.Polls.Polls;
import model.Forum.Polls.PollOptions;
import model.User;
import java.util.logging.Logger;
import java.util.List;

@ApplicationScoped
public class ForumPollOrm {
    private static final Logger log = Logger.getLogger(ForumPollOrm.class.getName());

    @Inject
    EntityManager em;

    @Transactional
    public Response createPoll(Long postId, Polls poll, Long userId) {
        log.info("ForumPollOrm/createPoll for post: " + postId);
        ForumPost post = em.find(ForumPost.class, postId);
        if (post == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Post nicht gefunden").build();
        }

        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Benutzer nicht gefunden").build();
        }

        if (!model.Users.Roles.hasRequiredRole(user.getRole(), post.getTopic().getCategory().getVisibility())) {
            return Response.status(Response.Status.FORBIDDEN).entity("Du hast keine Berechtigung für diese Kategorie.")
                    .build();
        }

        // Check if user is creator of post or admin
        if (!post.getCreatorObj().getId().equals(userId) && !user.getRole().equals("Admin")) {
            return Response.status(Response.Status.FORBIDDEN).entity("Nicht berechtigt").build();
        }

        // Check if a poll already exists for this post
        if (post.getPoll() != null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Dieser Post hat bereits eine Umfrage").build();
        }

        poll.setPost(post);
        for (PollOptions option : poll.getOptions()) {
            option.setPoll(poll);
            option.setVotes(0L);
        }

        em.persist(poll);
        post.setPoll(poll);
        em.merge(post);

        return Response.status(Response.Status.CREATED).entity(poll).build();
    }

    @Transactional
    public Response vote(Long pollId, List<Long> optionIds, Long userId) {
        log.info("ForumPollOrm/vote on poll: " + pollId + ", options: " + optionIds + " by user: " + userId);
        Polls poll = em.find(Polls.class, pollId);
        if (poll == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Umfrage nicht gefunden").build();
        }

        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Benutzer nicht gefunden").build();
        }

        if (!model.Users.Roles.hasRequiredRole(user.getRole(),
                poll.getPost().getTopic().getCategory().getVisibility())) {
            return Response.status(Response.Status.FORBIDDEN).entity("Du hast keine Berechtigung für diese Kategorie.")
                    .build();
        }

        if (optionIds == null || optionIds.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Keine Optionen ausgewählt").build();
        }

        // If not multiple selection and multiple selected, block
        if (!Boolean.TRUE.equals(poll.getAllowMultiple()) && optionIds.size() > 1) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Mehrfachauswahl ist für diese Umfrage nicht erlaubt").build();
        }

        // Remove previous votes in this poll by this user
        List<PollOptions> allOptions = em.createQuery("SELECT o FROM PollOptions o WHERE o.poll.id = :pollId", PollOptions.class)
                                         .setParameter("pollId", pollId)
                                         .getResultList();
        for (PollOptions option : allOptions) {
            if (option.getVotedUsers().contains(user)) {
                option.getVotedUsers().remove(user);
                option.setVotes(Math.max(0L, (option.getVotes() != null ? option.getVotes() : 0L) - 1));
                em.merge(option);
            }
        }

        // Add user to new selected options
        for (Long optionId : optionIds) {
            PollOptions option = em.find(PollOptions.class, optionId);
            if (option == null || !option.getPoll().getId().equals(pollId)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Ungültige Option").build();
            }
            if (!option.getVotedUsers().contains(user)) {
                option.getVotedUsers().add(user);
                option.setVotes((option.getVotes() != null ? option.getVotes() : 0L) + 1);
                em.merge(option);
            }
        }

        // Ensure user is in votedUsers list of the poll
        if (!poll.getVotedUsers().contains(user)) {
            poll.getVotedUsers().add(user);
            em.merge(poll);
        }

        em.flush();
        // Return fresh poll details with updated vote counts
        Polls updatedPoll = em.find(Polls.class, pollId);
        return Response.ok(updatedPoll).build();
    }

    public boolean hasVoted(Long pollId, Long userId) {
        Polls poll = em.find(Polls.class, pollId);
        if (poll == null)
            return false;
        User user = em.find(User.class, userId);
        if (user == null)
            return false;
        return poll.getVotedUsers().contains(user);
    }

    public List<Long> getVotedOptionIds(Long pollId, Long userId) {
        return em.createQuery(
            "SELECT o.id FROM PollOptions o JOIN o.votedUsers u WHERE o.poll.id = :pollId AND u.id = :userId",
            Long.class
        )
        .setParameter("pollId", pollId)
        .setParameter("userId", userId)
        .getResultList();
    }
}
