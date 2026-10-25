package orm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.List;
import model.Gallery;
import model.User;
import io.quarkus.cache.CacheInvalidateAll;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;

@ApplicationScoped
public class GalleryOrm {


    @Inject
    EntityManager em;

    @WithSpan
    public List<Gallery> getAllGalleries() {
        TypedQuery<Gallery> query = em.createQuery("SELECT g FROM Gallery g LEFT JOIN FETCH g.creator ORDER BY g.date DESC, g.id DESC", Gallery.class);
        return query.getResultList();
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "galleries"),
        @CacheInvalidateAll(cacheName = "galleries-by-id")
    })
    @WithSpan
    public Gallery addGallery(Gallery gallery, Long userId) {
        Telemetry.attr("user.id", userId);
        User user = em.find(User.class, userId);
        if (user == null) {
            Telemetry.warn("User not found", "user.id", userId);
            throw new IllegalArgumentException("Benutzer nicht gefunden.");
        }
        gallery.setCreator(user);
        em.persist(gallery);
        return gallery;
    }

    @WithSpan
    public Gallery getGalleryById(Long id) {
        Telemetry.attr("gallery.id", id);
        TypedQuery<Gallery> query = em.createQuery("SELECT g FROM Gallery g LEFT JOIN FETCH g.creator WHERE g.id = :id", Gallery.class);
        query.setParameter("id", id);
        List<Gallery> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "galleries"),
        @CacheInvalidateAll(cacheName = "galleries-by-id")
    })
    @WithSpan
    public Gallery updateGallery(Gallery gallery) {
        Telemetry.attr("gallery.id", gallery.getId());
        Gallery merged = em.merge(gallery);
        em.flush();
        return merged;
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "galleries"),
        @CacheInvalidateAll(cacheName = "galleries-by-id")
    })
    @WithSpan
    public void deleteGallery(Long id) {
        Telemetry.attr("gallery.id", id);
        Gallery g = em.find(Gallery.class, id);
        if (g != null) {
            em.remove(g);
        }
    }
}
