package orm;

import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.TypedQuery;
import javax.persistence.EntityManager;
//Logging zeug
import org.jboss.logging.Logger;

import model.Address;
import model.User;

@ApplicationScoped
public class AddressOrm {

    private static final Logger LOG = Logger.getLogger(AddressOrm.class);

    @Inject
    EntityManager em;

    public Address getAddressByUser(Long userId) {

        Address a = new Address();

        TypedQuery<Address> query = em.createQuery("SELECT u FROM Address u WHERE userId =: val", Address.class);
        query.setParameter("val", userId);

        try {
            a = query.getSingleResult();
        } catch (Exception e) {
            LOG.error("Fehler bei getAddressByUser");
        }

        return a;
    }

    public String addAddress(Address a, Long userId) {
        try {
            User usr = em.find(User.class, userId);
            usr.setAddress(a);
            em.persist(a);
            em.merge(usr);
        } catch (Exception e) {
            return "Fehler beim Hinzufügen der Addresse";
        }
        return "Addresse erfolgreich hinzugefügt";
    }

    public String updateAddress(Address a) {
        try {
            em.merge(a);
        } catch (Exception e) {
            return "Fehler beim Aktualisieren der Adresse";
        }
        return "Adresse erfolgreich aktualiseirt";
    }
}
