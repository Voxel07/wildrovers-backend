package orm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.logging.Logger;
import model.Gallery;
import model.User;

@ApplicationScoped
public class GalleryOrm {

    private static final Logger log = Logger.getLogger(GalleryOrm.class.getName());

    @Inject
    EntityManager em;

    public List<Gallery> getAllGalleries() {
        log.info("GalleryOrm/getAllGalleries");
        TypedQuery<Gallery> query = em.createQuery("SELECT g FROM Gallery g ORDER BY g.date DESC, g.id DESC", Gallery.class);
        return query.getResultList();
    }

    @Transactional
    public Gallery addGallery(Gallery gallery, Long userId) {
        log.info("GalleryOrm/addGallery by user: " + userId);
        User user = em.find(User.class, userId);
        if (user == null) {
            log.warning("User not found in DB");
            throw new IllegalArgumentException("Benutzer nicht gefunden.");
        }
        gallery.setCreator(user);
        em.persist(gallery);
        return gallery;
    }

    public Gallery getGalleryById(Long id) {
        log.info("GalleryOrm/getGalleryById: " + id);
        TypedQuery<Gallery> query = em.createQuery("SELECT g FROM Gallery g LEFT JOIN FETCH g.creator WHERE g.id = :id", Gallery.class);
        query.setParameter("id", id);
        List<Gallery> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Transactional
    public Gallery updateGallery(Gallery gallery) {
        log.info("GalleryOrm/updateGallery: " + gallery.getId());
        Gallery merged = em.merge(gallery);
        em.flush();
        return getGalleryById(merged.getId());
    }

    @Transactional
    public void deleteGallery(Long id) {
        log.info("GalleryOrm/deleteGallery: " + id);
        Gallery g = em.find(Gallery.class, id);
        if (g != null) {
            em.remove(g);
        }
    }
}
