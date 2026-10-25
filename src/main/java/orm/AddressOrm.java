package orm;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.EntityManager;

import model.Address;
import model.User;
import tools.Telemetry;

@ApplicationScoped
public class AddressOrm {


    @Inject
    EntityManager em;

    public Address getAddressByUser(Long userId) {

        Address a = new Address();

        TypedQuery<Address> query = em.createQuery("SELECT a FROM Address a WHERE a.userAddress.id = :val", Address.class);
        query.setParameter("val", userId);

        try {
            a = query.getSingleResult();
        } catch (Exception e) {
            Telemetry.warn("Address lookup failed", e, "user.id", userId);
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
