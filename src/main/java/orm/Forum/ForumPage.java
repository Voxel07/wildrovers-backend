package orm.Forum;

import java.util.List;

import jakarta.persistence.Query;

/**
 * One page of a forum list plus the total number of matching rows. Visibility is part of the
 * query, so the total and the page only contain rows the reader may see.
 */
public record ForumPage<T>(List<T> items, long total) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    /** Response header with the total row count of a paged list. */
    public static final String TOTAL_HEADER = "X-Total-Count";

    public static int firstResult(Integer page, Integer size) {
        return Math.max(0, page == null ? 0 : page) * pageSize(size);
    }

    public static int pageSize(Integer size) {
        if (size == null || size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    /**
     * JPQL condition that keeps rows whose category the reader may see. Rows without a category
     * and categories without a visibility count as public, as in the per-item checks.
     */
    static String visibilityClause(String categoryAlias, List<String> visibleRoles) {
        if (visibleRoles.isEmpty()) return "(" + categoryAlias + " IS NULL)";
        return "(" + categoryAlias + " IS NULL OR " + categoryAlias + ".visibility IS NULL OR "
                + categoryAlias + ".visibility = '' OR " + categoryAlias + ".visibility IN :visibleRoles)";
    }

    static void bindVisibility(Query query, List<String> visibleRoles) {
        if (!visibleRoles.isEmpty()) query.setParameter("visibleRoles", visibleRoles);
    }
}
