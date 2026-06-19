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
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidateAll;

@ApplicationScoped
public class ForumPollOrm {
    private static final Logger log = Logger.getLogger(ForumPollOrm.class.getName());

    @Inject
    EntityManager em;

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "poll-has-voted"),
        @CacheInvalidateAll(cacheName = "poll-voted-options")
    })
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

        poll.setPost(post);
        for (PollOptions option : poll.getOptions()) {
            option.setPoll(poll);
            option.setVotes(0L);
        }

        em.persist(poll);
        post.getPolls().add(poll);
        em.merge(post);

        return Response.status(Response.Status.CREATED).entity(poll).build();
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "poll-has-voted"),
        @CacheInvalidateAll(cacheName = "poll-voted-options")
    })
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

    @CacheResult(cacheName = "poll-has-voted")
    public boolean hasVoted(Long pollId, Long userId) {
        Polls poll = em.find(Polls.class, pollId);
        if (poll == null)
            return false;
        User user = em.find(User.class, userId);
        if (user == null)
            return false;
        return poll.getVotedUsers().contains(user);
    }

    @CacheResult(cacheName = "poll-voted-options")
    public List<Long> getVotedOptionIds(Long pollId, Long userId) {
        return em.createQuery(
            "SELECT o.id FROM PollOptions o JOIN o.votedUsers u WHERE o.poll.id = :pollId AND u.id = :userId",
            Long.class
        )
        .setParameter("pollId", pollId)
        .setParameter("userId", userId)
        .getResultList();
    }

    /**
     * Returns voter names grouped by option for a given poll.
     * Returns empty names if the poll is anonymous.
     */
    public jakarta.json.JsonArray getVoterNames(Long pollId) {
        Polls poll = em.find(Polls.class, pollId);
        if (poll == null) {
            return jakarta.json.Json.createArrayBuilder().build();
        }

        jakarta.json.JsonArrayBuilder result = jakarta.json.Json.createArrayBuilder();

        for (PollOptions option : poll.getOptions()) {
            jakarta.json.JsonArrayBuilder names = jakarta.json.Json.createArrayBuilder();

            // Only expose voter names if the poll is NOT anonymous
            if (!Boolean.TRUE.equals(poll.getAnonymous())) {
                // Force-load the lazy votedUsers collection
                List<User> voters = option.getVotedUsers();
                if (voters != null) {
                    for (User u : voters) {
                        names.add(u.getUserName());
                    }
                }
            }

            result.add(jakarta.json.Json.createObjectBuilder()
                .add("optionId", option.getId())
                .add("voterNames", names)
                .build());
        }

        return result.build();
    }

    /**
     * Updates an existing poll: question, allowMultiple, anonymous, and options.
     * Options with an ID are updated (text change, votes preserved).
     * Options without an ID are created as new.
     * Existing options not present in the update are deleted along with their votes.
     */
    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "poll-has-voted"),
        @CacheInvalidateAll(cacheName = "poll-voted-options")
    })
    public Response updatePoll(Long pollId, Polls updatedPoll, Long userId) {
        log.info("ForumPollOrm/updatePoll: " + pollId + " by user: " + userId);
        Polls poll = em.find(Polls.class, pollId);
        if (poll == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Umfrage nicht gefunden").build();
        }

        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Benutzer nicht gefunden").build();
        }

        ForumPost post = poll.getPost();
        if (post == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Zugehöriger Post nicht gefunden").build();
        }

        // Only post creator or admin can edit
        if (!post.getCreatorObj().getId().equals(userId) && !user.getRole().equals("Admin")) {
            return Response.status(Response.Status.FORBIDDEN).entity("Nicht berechtigt").build();
        }

        // Update poll fields
        if (updatedPoll.getQuestion() != null && !updatedPoll.getQuestion().isBlank()) {
            poll.setQuestion(updatedPoll.getQuestion());
        }
        poll.setAllowMultiple(updatedPoll.getAllowMultiple());
        poll.setAnonymous(updatedPoll.getAnonymous());

        // Collect IDs of options that should remain
        java.util.Set<Long> keptOptionIds = new java.util.HashSet<>();
        if (updatedPoll.getOptions() != null) {
            for (PollOptions incoming : updatedPoll.getOptions()) {
                if (incoming.getId() != null) {
                    // Existing option — update text, preserve votes
                    PollOptions existing = em.find(PollOptions.class, incoming.getId());
                    if (existing != null && existing.getPoll().getId().equals(pollId)) {
                        existing.setOptionText(incoming.getOptionText());
                        em.merge(existing);
                        keptOptionIds.add(incoming.getId());
                    }
                } else {
                    // New option
                    incoming.setPoll(poll);
                    incoming.setVotes(0L);
                    em.persist(incoming);
                }
            }
        }

        // Delete options that were removed from the update payload
        for (PollOptions existing : poll.getOptions()) {
            if (!keptOptionIds.contains(existing.getId())) {
                // Delete votes for this option first
                em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE option_id = :optId")
                    .setParameter("optId", existing.getId()).executeUpdate();
                em.remove(existing);
            }
        }

        em.flush();
        // Return fresh poll data
        Polls refreshed = em.find(Polls.class, pollId);
        return Response.ok(refreshed).build();
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "poll-has-voted"),
        @CacheInvalidateAll(cacheName = "poll-voted-options")
    })
    public Response deletePoll(Long pollId, Long userId) {
        log.info("ForumPollOrm/deletePoll: " + pollId + " by user: " + userId);
        Polls poll = em.find(Polls.class, pollId);
        if (poll == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Umfrage nicht gefunden").build();
        }

        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Benutzer nicht gefunden").build();
        }

        ForumPost post = poll.getPost();
        if (post == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Zugehöriger Post nicht gefunden").build();
        }

        // Only post creator or admin can delete
        if (!post.getCreatorObj().getId().equals(userId) && !user.getRole().equals("Admin")) {
            return Response.status(Response.Status.FORBIDDEN).entity("Nicht berechtigt").build();
        }

        // Delete poll option votes
        em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE option_id IN (SELECT id FROM FORUM_POLL_OPTIONS WHERE poll_id = :pollId)")
                .setParameter("pollId", pollId).executeUpdate();
        // Delete poll votes
        em.createNativeQuery("DELETE FROM FORUM_POLL_VOTES WHERE poll_id = :pollId")
                .setParameter("pollId", pollId).executeUpdate();
        // Delete poll options
        em.createQuery("DELETE FROM PollOptions o WHERE o.poll.id = :pollId")
                .setParameter("pollId", pollId).executeUpdate();
        // Remove poll from post's collection to satisfy orphanRemoval / cascade
        post.getPolls().remove(poll);
        em.merge(post);
        // Delete poll
        em.remove(poll);
        em.flush();

        return Response.ok("Umfrage erfolgreich gelöscht").build();
    }
}
