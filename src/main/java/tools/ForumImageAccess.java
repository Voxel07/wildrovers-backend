package tools;

import helper.UserPrincipalResolver;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import model.User;
import model.Users.Roles;

/** A signature grants image access; authorise the image's source post before issuing it. */
@RequestScoped
public class ForumImageAccess {
    @Inject EntityManager em;
    @Inject UserPrincipalResolver resolver;
    private final Map<Long, Boolean> readable = new HashMap<>();

    public Set<Long> readablePostIds(Set<Long> postIds) {
        Set<Long> missing = new HashSet<>(postIds);
        missing.removeAll(readable.keySet());
        if (!missing.isEmpty()) {
            User user;
            try { user = resolver.resolveUser(); }
            catch (WebApplicationException e) { user = null; }
            String role = user == null ? Roles.VSISITOR : user.getRole();
            missing.forEach(id -> readable.put(id, false));
            List<Object[]> rows = em.createQuery(
                    "SELECT p.id, c.visibility FROM ForumPost p JOIN p.topic t JOIN t.category c WHERE p.id IN :ids",
                    Object[].class).setParameter("ids", missing).getResultList();
            for (Object[] row : rows) {
                String required = (String) row[1];
                if (required == null || required.isBlank()) required = Roles.VSISITOR;
                readable.put((Long) row[0], Roles.hasRequiredRole(role, required));
            }
        }
        Set<Long> result = new HashSet<>();
        for (Long id : postIds) if (Boolean.TRUE.equals(readable.get(id))) result.add(id);
        return result;
    }
}
