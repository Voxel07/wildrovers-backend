package orm.Forum;

import jakarta.persistence.EntityManager;

/**
 * Atomic counter updates in the database. Java-side read-modify-write loses updates when two
 * requests change the same row at once; {@code SET x = x + d} does not. Counters never go below 0.
 */
final class ForumCounters {
    private ForumCounters() { }

    static void adjust(EntityManager em, String entity, String field, Long id, long delta) {
        if (id == null || delta == 0) return;
        String value = "COALESCE(e." + field + ", 0) + :delta";
        em.createQuery("UPDATE " + entity + " e SET e." + field + " = CASE WHEN " + value + " < 0 THEN 0 ELSE "
                + value + " END WHERE e.id = :id")
                .setParameter("delta", delta)
                .setParameter("id", id)
                .executeUpdate();
    }
}
