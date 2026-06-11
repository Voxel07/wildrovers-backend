package orm;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import io.quarkus.elytron.security.common.BcryptUtil;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;

import jakarta.json.JsonObject;

//Eigene
import model.User;
import orm.Forum.ForumCategoryOrm;
import orm.Secrets.SecretOrm;
import orm.UserStuff.ActivityForumOrm;
import tools.Email;
import resources.JWT;
import tools.Time;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

//Coockie
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class UserOrm {

    private static final Logger log = Logger.getLogger(UserOrm.class.getName());

    @Inject
    EntityManager em;

    @Inject
    ActivityForumOrm activityForumOrm;

    @Inject
    SecretOrm secretOrm;

    @Inject
    Email email;

    @Inject
    ForumCategoryOrm forumCategoryOrm;

    public Long getEventsAttendedCount(Long userId) {
        try {
            return em
                    .createQuery(
                            "SELECT COUNT(ea) FROM EventAttendance ea WHERE ea.user.id = :userId AND ea.status = 'YES'",
                            Long.class)
                    .setParameter("userId", userId)
                    .getSingleResult();
        } catch (Exception e) {
            return 0L;
        }
    }

    public List<User> getUsers() {
        log.info("UserOrm/getUsers");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        List<User> users = query.getResultList();
        for (User u : users) {
            u.setEventsAttended(getEventsAttendedCount(u.getId()));
        }
        return users;
    }

    public List<User> getUserById(Long userId) {
        log.info("UserOrm/getUserById");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE id =: val", User.class);
        query.setParameter("val", userId);
        List<User> users = query.getResultList();
        for (User u : users) {
            u.setEventsAttended(getEventsAttendedCount(u.getId()));
        }
        return users;
    }

    public List<User> getUserByUsername(String userName) {
        log.info("UserOrm/getUserByUsername");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE userName =: val", User.class);
        query.setParameter("val", userName);
        return query.getResultList();
    }

    public User findByUsername(String username) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.userName = :val", User.class)
                    .setParameter("val", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public User findByEmail(String email) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.email = :val", User.class)
                    .setParameter("val", email)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public User createOidcUser(String username, String email, String firstName, String lastName, String role) {
        log.info("UserOrm/createOidcUser: " + username + " (" + email + ")");
        User usr = new User();
        usr.setUserName(username);
        usr.setEmail(email);
        usr.setFirstName((firstName != null && !firstName.isBlank()) ? firstName : "OIDC");
        usr.setLastName((lastName != null && !lastName.isBlank()) ? lastName : "User");
        usr.setRole(role != null ? role : "Besucher");
        usr.setActive(true);
        usr.setPassword("OIDC_DUMMY"); // Set dummy value for transient validation
        usr.setRegDate(Time.currentTimeInMillis());

        try {
            em.persist(usr);
            activityForumOrm.addActivityForum(usr.getId());

            // Register secret for user, marked verified since they authenticated via OIDC
            String verificationId = secretOrm.generateVerificationId();
            String dummyHash = BcryptUtil.bcryptHash(java.util.UUID.randomUUID().toString());
            secretOrm.addSecret(usr.getId(), true, verificationId, dummyHash);

            return usr;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to create JIT user from OIDC", e);
            return null;
        }
    }

    @Transactional
    public Response addUser(User usr) {
        log.info("UserOrm/addUser");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2",
                User.class);
        query.setParameter("val1", usr.getUserName());
        query.setParameter("val2", usr.getEmail());

        if (!query.getResultList().isEmpty()) {
            return Response.status(406).entity("Nutzer bereits bekannt").build();
        }

        String plainPassword = usr.getPassword();
        String passwordHash = BcryptUtil.bcryptHash(plainPassword);
        usr.setPassword(passwordHash); // Set dummy value for transient validation
        usr.setRegDate(Time.currentTimeInMillis());
        usr.setActive(true);
        usr.setRole("Besucher"); // Default role is Guest for now

        // Nutzer einfügen
        try {
            em.persist(usr);
        } catch (Exception e) {
            // TODO: Remove e from error message
            return Response.status(500).entity("Fehler beim Nutzer einfügen" + e).build();
        }

        // NOTE:
        /**
         * Needs to be after the User has been persisted to the Database so we can get
         * the ID;
         */
        // create Activity logs
        Long userId = usr.getId();
        activityForumOrm.addActivityForum(userId);

        /*
         * Generate secrets
         */
        String verificationId = secretOrm.generateVerificationId();
        secretOrm.addSecret(userId, false, verificationId, passwordHash);
        // Id zurückgeben

        /**
         * Send Email so that the user can verify his acc
         */
        email.sendVerificationMail(usr.getEmail(), userId, verificationId);

        return Response.status(201).entity("Nutzer erfolgreich erstellt").build();

    }

    @Transactional
    public String updateUser(User u) {
        log.info("UserOrm/updateUser");

        boolean error = false;
        String errorMSG = "";

        TypedQuery<User> query = em.createQuery(
                "SELECT u FROM User u WHERE u.userName =: UserName OR u.email =: Email OR u.id =: Id", User.class);

        query.setParameter("UserName", u.getUserName());
        query.setParameter("Email", u.getEmail());
        query.setParameter("Id", u.getId());

        List<User> userAusDB = query.getResultList();
        if (userAusDB == null || userAusDB.isEmpty()) {
            return "Keinen Nutzer mit diesen Daten gefunden";
        }

        User dbUser = null;
        for (User aktUser : userAusDB) {
            if (aktUser.getId().equals(u.getId())) {
                dbUser = aktUser;
                break;
            }
        }

        if (dbUser == null) {
            return "Keinen Nutzer mit diesen Daten gefunden";
        }

        // Check all users in database to detect duplicate username/email conflicts.
        for (User aktUser : userAusDB) {
            // Überprüfen ob die ID die gleiche ist.
            if (!aktUser.getId().equals(u.getId()) && !error) {
                if (aktUser.getUserName().equals(u.getUserName())) {
                    error = true;
                    errorMSG = "Username bereits vergeben";
                }
                // Oder die Email
                else if (aktUser.getEmail().equals(u.getEmail())) {
                    error = true;
                    errorMSG = "Email bereits vergeben";
                }
            }
        }

        if (error)
            return errorMSG; // Return the specific reason.

        // No unique values are harmed, so the first and only entry in the list has to
        // be the correct one.
        if (!u.getUserName().equals(dbUser.getUserName())) {
            try {
                forumCategoryOrm.updateCategoryUserName(u.getUserName(), dbUser.getUserName());
            } catch (Exception e) {

                return "Fehler beim Updaten der Kategorien" + e.toString();
            }
            // TODO: Update Topics, Posts, Answers as well
        }

        // Update fields safely on dbUser
        dbUser.setUserName(u.getUserName());
        dbUser.setEmail(u.getEmail());
        if (u.getFirstName() != null && !u.getFirstName().isBlank()) {
            dbUser.setFirstName(u.getFirstName());
        }
        if (u.getLastName() != null && !u.getLastName().isBlank()) {
            dbUser.setLastName(u.getLastName());
        }
        if (u.getRole() != null && !u.getRole().isBlank()) {
            dbUser.setRole(u.getRole());
        }
        dbUser.setActive(u.isActive());
        if (u.getYearlyFeePaid() != null) {
            dbUser.setYearlyFeePaid(u.getYearlyFeePaid());
        }
        if (u.getPassword() != null && !u.getPassword().isBlank()) {
            // Only re-hash if the value is a plain-text password, not already a BCrypt hash.
            // BCrypt hashes always start with '$2' — re-hashing them would corrupt the stored password.
            String incoming = u.getPassword();
            if (!incoming.startsWith("$2")) {
                if (dbUser.getSecret() != null) {
                    dbUser.getSecret().setPassword(BcryptUtil.bcryptHash(incoming));
                }
            }
        }
        try {
            em.merge(dbUser);
            errorMSG = "User erfolgreich aktualisiert";
        } catch (Exception e) {
            errorMSG = "Fehler beim Updaten des User";
        }

        return errorMSG;
    }

    @Transactional
    public Response loginUser(User usr) {
        log.info("UserOrm/loginUser");
        if (usr.getUserName() == null && usr.getEmail() == null)
            return Response.status(401).entity("Gib was an").build();
        if (usr.getPassword() == null)
            return Response.status(401).entity("Password nicht gesetzt").build();

        // try {
        // log.info("Waiting 10 sec");

        // Thread.sleep(3000);
        // } catch (InterruptedException e1) {
        // // TODO Auto-generated catch block
        // e1.printStackTrace();
        // }
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val1",
                User.class);
        query.setParameter("val1", usr.getUserName());
        // query.setParameter("val2", usr.getEmail());

        // Find user in DB
        User user;
        try {
            user = query.getSingleResult();
        } catch (Exception e) {
            log.info("Kein user gefunden");
            // TODO: Change text to: "Benutzername oder Password flasch"
            return Response.status(401).entity("Benutzer nicht gefunden").build();
        }

        if (!user.getSecret().getIsVerifyed().booleanValue())
            return Response.status(401).entity("Bitte verifiziere dein Konto").build();

        // Verify Password
        try {
            String storedPasswordHash = user.getSecret() != null ? user.getSecret().getPassword() : null;
            if (storedPasswordHash == null || !verifyBCryptPassword(storedPasswordHash, usr.getPassword())) {
                log.info("Falsches PW");
                // TODO: Change text to: "Benutzername oder Password flasch"
                return Response.status(401).entity("Falsches Password").build();

            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(401).entity("Fehler bei der Passwortprüfung").build();
        }

        user.setLastLogin(Time.currentTimeInMillis());
        em.merge(user);

        // Return cookie and Auth Object
        /* For now there is no refresh token */
        String token = JWT.generator(user);
        JsonObject reactAuthObject = JWT.createReactAuthObject(token, user);
        NewCookie cookie = JWT.generateCookie(token);

        return Response.status(200).entity(reactAuthObject).cookie(cookie).build();
    }

    public Response logoutUser() {
        return Response.ok(JWT.removeCookie()).build();
    }

    public static boolean verifyBCryptPassword(String bCryptPasswordHash, String passwordToVerify)
            throws Exception {

        WildFlyElytronPasswordProvider provider = new WildFlyElytronPasswordProvider();

        // 1. Create a BCrypt Password Factory
        PasswordFactory passwordFactory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT, provider);

        // 2. Decode the hashed user password
        Password userPasswordDecoded = ModularCrypt.decode(bCryptPasswordHash);

        // 3. Translate the decoded user password object to one which is consumable by
        // this factory.
        Password userPasswordRestored = passwordFactory.translate(userPasswordDecoded);

        // Verify existing user password you want to verify
        return passwordFactory.verify(userPasswordRestored, passwordToVerify.toCharArray());
    }

    public List<User> getTeamMembers() {
        log.info("UserOrm/getTeamMembers");
        TypedQuery<User> query = em.createQuery(
                "SELECT u FROM User u WHERE u.role IN ('Admin', 'Vorstand', 'Mitglied', 'Frischling') AND u.isActive = true",
                User.class);
        return query.getResultList();
    }

    @Transactional
    public User updateUserProfile(Long userId, String phrase, java.time.LocalDate birthday, String firstName,
            String lastName, String email) {
        log.info("UserOrm/updateUserProfile");
        User user = em.find(User.class, userId);
        if (user != null) {
            if (firstName != null && !firstName.isBlank()) {
                user.setFirstName(firstName);
            }
            if (lastName != null && !lastName.isBlank()) {
                user.setLastName(lastName);
            }
            if (email != null && !email.isBlank()) {
                User existing = findByEmail(email);
                if (existing != null && !existing.getId().equals(userId)) {
                    throw new IllegalArgumentException(
                            "Diese E-Mail-Adresse wird bereits von einem anderen Benutzer verwendet.");
                }
                user.setEmail(email);
            }
            user.setPhrase(phrase);
            user.setBirthday(birthday);
            em.merge(user);
        }
        return user;
    }

    @Transactional
    public void updateUserPhotoUrl(Long userId, String photoUrl) {
        log.info("UserOrm/updateUserPhotoUrl");
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setPhotoUrl(photoUrl);
            em.merge(user);
        }
    }

    @Transactional
    public void updateUserBackgroundUrl(Long userId, String backgroundUrl) {
        log.info("UserOrm/updateUserBackgroundUrl");
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setBackgroundUrl(backgroundUrl);
            em.merge(user);
        }
    }

    @Transactional
    public Response deleteUser(Long userId) {
        log.info("UserOrm/deleteUser: " + userId);
        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(404).entity("Benutzer nicht gefunden").build();
        }
        try {
            // 1. Delete EventAttendance records first.
            //    EventAttendance has a user_id FK with no CascadeType set on the User side,
            //    so Hibernate will NOT delete them automatically. Leaving them causes a
            //    FK constraint violation when the User row is deleted, which is why the
            //    first delete attempt only removes the Secret (the JPQL delete commits to DB)
            //    while the User removal fails during flush.
            em.createQuery("DELETE FROM EventAttendance ea WHERE ea.user.id = :uid")
                    .setParameter("uid", userId)
                    .executeUpdate();

            // 2. Delete Secret via JPQL (owns the user_id FK, must go before User).
            em.createQuery("DELETE FROM Secret s WHERE s.user.id = :uid")
                    .setParameter("uid", userId)
                    .executeUpdate();

            // 3. Flush and clear the persistence context.
            //    JPQL bulk deletes bypass Hibernate's PC (first-level cache), leaving it
            //    out of sync with the DB. Without this, em.remove(user) can fail because
            //    Hibernate still sees the old Secret entity as "managed" and tries to
            //    interact with it during the cascade analysis at flush time.
            em.flush();
            em.clear();

            // 4. Re-fetch the user on the now-clean PC and remove it.
            //    Hibernate will cascade to Address, ActivityForum, Phones, and Forum
            //    entities as configured via CascadeType.ALL on those relationships.
            user = em.find(User.class, userId);
            if (user == null) {
                // Secret was already deleted above; user was somehow gone — treat as success.
                return Response.ok("Benutzer erfolgreich gelöscht").build();
            }

            log.info("Lösche Benutzer: " + user.getUserName());
            em.remove(user);
            return Response.ok("Benutzer erfolgreich gelöscht").build();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error deleting user " + userId, e);
            return Response.status(500).entity("Fehler beim Löschen des Benutzers: " + e.getMessage()).build();
        }
    }


    /**
     * Targeted role-only update — avoids going through the full updateUser() path
     * which could inadvertently trigger password re-hashing.
     */
    @Transactional
    public void updateUserRole(Long userId, String role) {
        log.info("UserOrm/updateUserRole: userId=" + userId + " role=" + role);
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setRole(role);
            em.merge(user);
        }
    }

}

