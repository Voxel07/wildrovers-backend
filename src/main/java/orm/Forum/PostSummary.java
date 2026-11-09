package orm.Forum;

/**
 * One row of a post list (topic page, "my posts"). Carries everything the list shows but not
 * the post body, which is a LONGTEXT that would otherwise be loaded and run through image
 * signing for every row. The single-post request still returns the full entity.
 * Registered for reflection because it is only reachable through a Response entity.
 */
@io.quarkus.runtime.annotations.RegisterForReflection
public record PostSummary(Long id, String title, String creator, String creatorPhotoUrl,
        Long creationDate, Long editDate, Long likes, Long dislikes, Long views, Long answerCount,
        Long topicId, String topicName, Long categoryId, String categoryName, boolean viewed) {

    static PostSummary of(Object[] row) {
        return new PostSummary((Long) row[0], (String) row[1], row[2] != null ? (String) row[2] : "deleted",
                (String) row[3], (Long) row[4], (Long) row[5], (Long) row[6], (Long) row[7], (Long) row[8],
                (Long) row[9], (Long) row[10], (String) row[11], (Long) row[12], (String) row[13], false);
    }

    public PostSummary withViewed(boolean value) {
        return new PostSummary(id, title, creator, creatorPhotoUrl, creationDate, editDate, likes, dislikes,
                views, answerCount, topicId, topicName, categoryId, categoryName, value);
    }
}
