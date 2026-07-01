package orm;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheInvalidateAll;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;

import jakarta.json.JsonObject;

//Eigene
import model.User;
import model.Event;
import model.Gallery;
import model.YearlyFee;
import orm.Forum.ForumCategoryOrm;
import orm.Secrets.SecretOrm;
import orm.UserStuff.ActivityForumOrm;
import tools.Email;
import tools.GeoIPService;
import helper.RequestIpCapture;
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

    @Inject
    RequestIpCapture ipCapture;

    @Inject
    GeoIPService geoIPService;

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

    private void populateEventsAttended(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(User::getId).toList();
        try {
            List<Object[]> counts = em.createQuery(
                    "SELECT ea.user.id, COUNT(ea) FROM EventAttendance ea WHERE ea.user.id IN :userIds AND ea.status = 'YES' GROUP BY ea.user.id",
                    Object[].class)
                    .setParameter("userIds", userIds)
                    .getResultList();

            java.util.Map<Long, Long> countsMap = new java.util.HashMap<>();
            for (Object[] row : counts) {
                countsMap.put((Long) row[0], (Long) row[1]);
            }

            for (User u : users) {
                u.setEventsAttended(countsMap.getOrDefault(u.getId(), 0L));
            }
        } catch (Exception e) {
            log.warning("Failed to populate events attended count: " + e.getMessage());
        }
    }

    /**
     * Populates transient fields hasPaidCurrentYear and paidYears on each user.
     */
    private void populateYearlyFees(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        int currentYear = java.time.Year.now().getValue();
        List<Long> userIds = users.stream().map(User::getId).toList();
        try {
            List<YearlyFee> allFees = em.createQuery(
                    "SELECT yf FROM YearlyFee yf WHERE yf.user.id IN :userIds ORDER BY yf.feeYear",
                    YearlyFee.class)
                    .setParameter("userIds", userIds)
                    .getResultList();

            java.util.Map<Long, java.util.List<Integer>> feesByUser = new java.util.HashMap<>();
            for (YearlyFee yf : allFees) {
                feesByUser.computeIfAbsent(yf.getUser().getId(), k -> new java.util.ArrayList<>())
                        .add(yf.getFeeYear());
            }

            for (User u : users) {
                java.util.List<Integer> years = feesByUser.getOrDefault(u.getId(), java.util.Collections.emptyList());
                u.setPaidYears(years);
                u.setHasPaidCurrentYear(years.contains(currentYear));
            }
        } catch (Exception e) {
            log.warning("Failed to populate yearly fees: " + e.getMessage());
        }
    }

    public List<User> getUsers() {
        log.info("UserOrm/getUsers");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        List<User> users = query.getResultList();
        populateEventsAttended(users);
        populateYearlyFees(users);
        return users;
    }

    public List<User> getUserById(Long userId) {
        log.info("UserOrm/getUserById");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE id =: val", User.class);
        query.setParameter("val", userId);
        List<User> users = query.getResultList();
        populateEventsAttended(users);
        populateYearlyFees(users);
        return users;
    }

    public List<User> getUserByUsername(String userName) {
        log.info("UserOrm/getUserByUsername");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE userName =: val", User.class);
        query.setParameter("val", userName);
        List<User> users = query.getResultList();
        populateYearlyFees(users);
        return users;
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
    @CacheInvalidateAll(cacheName = "team-members")
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
    @CacheInvalidateAll(cacheName = "team-members")
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

        try {
            em.persist(usr);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Fehler beim Nutzer einfügen", e);
            return Response.status(500).entity("Fehler beim Nutzer einfügen").build();
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
        try {
            email.sendVerificationMail(usr.getEmail(), verificationId);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Fehler beim Senden der Verifizierungs-E-Mail: " + e.getMessage(), e);
            throw new jakarta.ws.rs.WebApplicationException(
                Response.status(500)
                    .entity("Fehler beim Senden der Verifizierungs-E-Mail: " + e.getMessage())
                    .build()
            );
        }

        return Response.status(201).entity("Nutzer erfolgreich erstellt").build();

    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
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
            // Topics, Posts, and Answers reference the User entity via JPA FK relations,
            // so they automatically reflect the updated username — no manual update needed.
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
        if (u.getIsBlocked() != null) {
            dbUser.setIsBlocked(u.getIsBlocked());
        }
        // Handle yearlyFeePaid toggle: add/remove YearlyFee for current year
        if (u.getHasPaidCurrentYear() != null) {
            int currentYear = java.time.Year.now().getValue();
            boolean currentlyPaid = dbUser.getYearlyFees().stream()
                    .anyMatch(yf -> yf.getFeeYear() == currentYear);
            if (u.getHasPaidCurrentYear() && !currentlyPaid) {
                YearlyFee yf = new YearlyFee(currentYear, dbUser);
                em.persist(yf);
                dbUser.getYearlyFees().add(yf);
            } else if (!u.getHasPaidCurrentYear() && currentlyPaid) {
                dbUser.getYearlyFees().removeIf(yf -> yf.getFeeYear() == currentYear);
                em.createQuery("DELETE FROM YearlyFee yf WHERE yf.user.id = :uid AND yf.feeYear = :year")
                        .setParameter("uid", dbUser.getId())
                        .setParameter("year", currentYear)
                        .executeUpdate();
            }
        }
        if (u.getCanCreateCategory() != null) {
            dbUser.setCanCreateCategory(u.getCanCreateCategory());
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

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val1",
                User.class);
        query.setParameter("val1", usr.getUserName() != null ? usr.getUserName() : usr.getEmail());

        // Find user in DB
        User user;
        try {
            user = query.getSingleResult();
        } catch (Exception e) {
            log.info("Kein user gefunden");
            return Response.status(401).entity("Benutzername oder Passwort falsch").build();
        }

        if (!user.getSecret().getIsVerifyed().booleanValue())
            return Response.status(401).entity("Bitte verifiziere dein Konto").build();

        if (user.getIsBlocked()) {
            return Response.status(403).entity("Dein Account wurde gesperrt.").build();
        }

        // Verify Password
        try {
            String storedPasswordHash = user.getSecret() != null ? user.getSecret().getPassword() : null;
            if (storedPasswordHash == null || !verifyBCryptPassword(storedPasswordHash, usr.getPassword())) {
                log.info("Falsches PW");
                return Response.status(401).entity("Benutzername oder Passwort falsch").build();

            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(401).entity("Fehler bei der Passwortprüfung").build();
        }

        user.setLastLogin(Time.currentTimeInMillis());

        // Save IP and country for session hijacking detection
        String clientIp = ipCapture.getClientIp();
        user.setLastLoginIp(clientIp);
        if (clientIp != null && !"unknown".equals(clientIp)) {
            String country = geoIPService.getCountry(clientIp);
            user.setLastLoginCountry(country);
            log.info("Login from IP: " + clientIp + " country: " + country);
        }

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

    @CacheResult(cacheName = "team-members")
    public List<User> getTeamMembers() {
        log.info("UserOrm/getTeamMembers");
        TypedQuery<User> query = em.createQuery(
                "SELECT u FROM User u WHERE u.role IN ('Admin', 'Vorstand', 'Mitglied', 'Frischling') AND u.isActive = true",
                User.class);
        List<User> users = query.getResultList();
        populateYearlyFees(users);
        return users;
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
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
    @CacheInvalidateAll(cacheName = "team-members")
    public void updateUserPhotoUrl(Long userId, String photoUrl) {
        log.info("UserOrm/updateUserPhotoUrl");
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setPhotoUrl(photoUrl);
            em.merge(user);
        }
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
    public void updateUserBackgroundUrl(Long userId, String backgroundUrl) {
        log.info("UserOrm/updateUserBackgroundUrl");
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setBackgroundUrl(backgroundUrl);
            em.merge(user);
        }
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "team-members"),
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id"),
        @CacheInvalidateAll(cacheName = "galleries"),
        @CacheInvalidateAll(cacheName = "galleries-by-id")
    })
    public Response deleteUser(Long userId) {
        return deleteUserWithOptions(userId, true, true, false, true, false);
    }

    @Transactional
    @CacheInvalidateAll.List({
        @CacheInvalidateAll(cacheName = "team-members"),
        @CacheInvalidateAll(cacheName = "events"),
        @CacheInvalidateAll(cacheName = "upcoming-events"),
        @CacheInvalidateAll(cacheName = "events-by-id"),
        @CacheInvalidateAll(cacheName = "events-by-post-id"),
        @CacheInvalidateAll(cacheName = "galleries"),
        @CacheInvalidateAll(cacheName = "galleries-by-id")
    })
    public Response deleteUserWithOptions(Long userId, boolean deleteAccount, boolean deleteEvents, boolean deletePosts, boolean deleteGallery, boolean hardDelete) {
        log.info("UserOrm/deleteUserWithOptions: " + userId + " (account=" + deleteAccount + ", events=" + deleteEvents + ", posts=" + deletePosts + ", gallery=" + deleteGallery + ")");
        User user = em.find(User.class, userId);
        if (user == null) {
            return Response.status(404).entity("Benutzer nicht gefunden").build();
        }
        try {
            if (deleteEvents) {
                em.createQuery("DELETE FROM EventAttendance ea WHERE ea.event.id IN (SELECT e.id FROM Event e WHERE e.creator.id = :uid)")
                        .setParameter("uid", userId).executeUpdate();
                em.createQuery("DELETE FROM EventAttendance ea WHERE ea.user.id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createQuery("DELETE FROM Event e WHERE e.creator.id = :uid")
                        .setParameter("uid", userId).executeUpdate();
            }

            if (deleteGallery) {
                em.createQuery("DELETE FROM Gallery g WHERE g.creator.id = :uid")
                        .setParameter("uid", userId).executeUpdate();
            }

            if (deletePosts) {
                // ── Smart cascade: delete user's forum content bottom-up ──
                // 1. Delete pictures of answers by this user, then delete the answers
                em.createNativeQuery("DELETE FROM FORUM_PICTURE WHERE answer_id IN (SELECT id FROM FORUM_ANSWERS WHERE user_id = :uid)")
                        .setParameter("uid", userId).executeUpdate();
                em.createNativeQuery("DELETE FROM FORUM_ANSWERS WHERE user_id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                // 2. Identify user's posts that still have answers from other users
                @SuppressWarnings("unchecked")
                List<Object> postsWithOtherAnswers = em.createNativeQuery(
                        "SELECT DISTINCT fp.id FROM FORUM_POSTS fp "
                        + "INNER JOIN FORUM_ANSWERS fa ON fa.post_id = fp.id "
                        + "WHERE fp.user_id = :uid")
                        .setParameter("uid", userId).getResultList();

                @SuppressWarnings("unchecked")
                List<Object> allUserPostIds = em.createNativeQuery(
                        "SELECT id FROM FORUM_POSTS WHERE user_id = :uid")
                        .setParameter("uid", userId).getResultList();

                java.util.Set<Long> postsToKeep = new java.util.HashSet<>();
                for (Object id : postsWithOtherAnswers) postsToKeep.add(((Number) id).longValue());

                java.util.List<Long> postsToDelete = new java.util.ArrayList<>();
                for (Object id : allUserPostIds) {
                    Long pid = ((Number) id).longValue();
                    if (!postsToKeep.contains(pid)) postsToDelete.add(pid);
                }

                // Delete posts that have no remaining answers
                if (!postsToDelete.isEmpty()) {
                    em.createNativeQuery("DELETE FROM FORUM_PICTURE WHERE answer_id IN (SELECT id FROM FORUM_ANSWERS WHERE post_id IN (:pids))")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_ANSWERS WHERE post_id IN (:pids)")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_PICTURE WHERE post_id IN (:pids)")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE option_id IN (SELECT id FROM FORUM_POLL_OPTIONS WHERE poll_id IN (SELECT id FROM FORUM_POLLS WHERE post_id IN (:pids)))")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POLL_VOTES WHERE poll_id IN (SELECT id FROM FORUM_POLLS WHERE post_id IN (:pids))")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POLL_OPTIONS WHERE poll_id IN (SELECT id FROM FORUM_POLLS WHERE post_id IN (:pids))")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POLLS WHERE post_id IN (:pids)")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POST_VOTES WHERE post_id IN (:pids)")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POST_VIEWS WHERE post_id IN (:pids)")
                            .setParameter("pids", postsToDelete).executeUpdate();
                    em.createNativeQuery("DELETE FROM FORUM_POSTS WHERE id IN (:pids)")
                            .setParameter("pids", postsToDelete).executeUpdate();
                }

                // Nullify creator on posts kept because they still have answers
                if (!postsToKeep.isEmpty()) {
                    java.util.List<Long> keepList = new java.util.ArrayList<>(postsToKeep);
                    em.createNativeQuery("UPDATE FORUM_POSTS SET user_id = NULL WHERE id IN (:pids) AND user_id = :uid")
                            .setParameter("pids", keepList).setParameter("uid", userId).executeUpdate();
                    em.createNativeQuery("UPDATE FORUM_POSTS SET editor_id = NULL WHERE id IN (:pids) AND editor_id = :uid")
                            .setParameter("pids", keepList).setParameter("uid", userId).executeUpdate();
                }

                // Clean up user's own poll/vote/view records
                em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE user_id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createNativeQuery("DELETE FROM FORUM_POST_VOTES WHERE user_id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createNativeQuery("DELETE FROM FORUM_POST_VIEWS WHERE user_id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                // 3. Smart cascade for topics: delete empty ones, nullify others
                @SuppressWarnings("unchecked")
                List<Object> userTopicIds = em.createNativeQuery(
                        "SELECT id FROM FORUM_TOPIC WHERE user_id = :uid")
                        .setParameter("uid", userId).getResultList();

                if (!userTopicIds.isEmpty()) {
                    java.util.List<Long> topicIdList = new java.util.ArrayList<>();
                    for (Object id : userTopicIds) topicIdList.add(((Number) id).longValue());

                    @SuppressWarnings("unchecked")
                    List<Object> topicsWithPosts = em.createNativeQuery(
                            "SELECT DISTINCT ft.id FROM FORUM_TOPIC ft "
                            + "INNER JOIN FORUM_POSTS fp ON fp.topic_id = ft.id "
                            + "WHERE ft.id IN (:tids)")
                            .setParameter("tids", topicIdList).getResultList();

                    java.util.Set<Long> topicsToKeepSet = new java.util.HashSet<>();
                    for (Object id : topicsWithPosts) topicsToKeepSet.add(((Number) id).longValue());

                    java.util.List<Long> topicsToDelete = new java.util.ArrayList<>();
                    java.util.List<Long> topicsToNullify = new java.util.ArrayList<>();
                    for (Long tid : topicIdList) {
                        if (topicsToKeepSet.contains(tid)) topicsToNullify.add(tid);
                        else topicsToDelete.add(tid);
                    }

                    if (!topicsToDelete.isEmpty()) {
                        em.createNativeQuery("DELETE FROM FORUM_TOPIC WHERE id IN (:tids)")
                                .setParameter("tids", topicsToDelete).executeUpdate();
                    }
                    if (!topicsToNullify.isEmpty()) {
                        em.createNativeQuery("UPDATE FORUM_TOPIC SET user_id = NULL WHERE id IN (:tids)")
                                .setParameter("tids", topicsToNullify).executeUpdate();
                    }
                }

                // 4. Categories: never delete, only nullify creator
                em.createQuery("UPDATE ForumCategory c SET c.creator = null WHERE c.creator.id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                if (user.getActivityForum() != null) {
                    user.getActivityForum().setPostCount(0L);
                    user.getActivityForum().setAnswerCount(0L);
                }
            }

            if (deleteAccount) {
                // Delete EventAttendance records to prevent FK violations
                em.createQuery("DELETE FROM EventAttendance ea WHERE ea.user.id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                if (!deleteEvents) {
                    em.createQuery("UPDATE Event e SET e.creator = null WHERE e.creator.id = :uid")
                            .setParameter("uid", userId).executeUpdate();
                }
                if (!deleteGallery) {
                    em.createQuery("UPDATE Gallery g SET g.creator = null WHERE g.creator.id = :uid")
                            .setParameter("uid", userId).executeUpdate();
                }

                // Nullify mentor relationship
                em.createQuery("UPDATE User u SET u.mentor = null WHERE u.mentor.id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                // Delete Secret
                em.createQuery("DELETE FROM Secret s WHERE s.user.id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                // Decrement votes in PollOptions
                try {
                    @SuppressWarnings("unchecked")
                    List<Object> optionIds = em.createNativeQuery("SELECT option_id FROM FORUM_POLL_OPTION_VOTES WHERE user_id = :uid")
                            .setParameter("uid", userId).getResultList();
                    for (Object optionIdObj : optionIds) {
                        Long optionId = ((Number) optionIdObj).longValue();
                        em.createQuery("UPDATE PollOptions o SET o.votes = o.votes - 1 WHERE o.id = :oid AND o.votes > 0")
                                .setParameter("oid", optionId).executeUpdate();
                    }
                } catch (Exception e) {
                    log.warning("Failed to decrement user poll votes: " + e.getMessage());
                }

                em.createNativeQuery("DELETE FROM FORUM_POLL_OPTION_VOTES WHERE user_id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createNativeQuery("DELETE FROM FORUM_POLL_VOTES WHERE user_id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createQuery("DELETE FROM ForumPostVote v WHERE v.user.id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createQuery("DELETE FROM ForumPostView v WHERE v.user.id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                // Nullify creator/editor on remaining forum entities
                if (!deletePosts) {
                    em.createQuery("UPDATE ForumCategory c SET c.creator = null WHERE c.creator.id = :uid")
                            .setParameter("uid", userId).executeUpdate();
                    em.createQuery("UPDATE ForumTopic t SET t.creator = null WHERE t.creator.id = :uid")
                            .setParameter("uid", userId).executeUpdate();
                    em.createQuery("UPDATE ForumPost p SET p.creator = null WHERE p.creator.id = :uid")
                            .setParameter("uid", userId).executeUpdate();
                    em.createQuery("UPDATE ForumAnswer a SET a.creator = null WHERE a.creator.id = :uid")
                            .setParameter("uid", userId).executeUpdate();
                }
                em.createQuery("UPDATE ForumPost p SET p.editor = null WHERE p.editor.id = :uid")
                        .setParameter("uid", userId).executeUpdate();
                em.createQuery("UPDATE ForumAnswer a SET a.editor = null WHERE a.editor.id = :uid")
                        .setParameter("uid", userId).executeUpdate();

                em.flush();
                em.clear();

                // Block the user instead of deleting to prevent OIDC JIT re-provisioning.
                // Keep email + userName so the OIDC lookup finds this record and
                // the isBlocked check in UserPrincipalResolver returns 403.
                user = em.find(User.class, userId);
                if (user != null) {
                    if (hardDelete) {
                        em.remove(user);
                    } else {
                        user.setActive(false);
                        user.setIsBlocked(true);
                        user.setFirstName("Gelöscht");
                        user.setLastName("Gelöscht");
                        user.setPhrase(null);
                        user.setPhotoUrl(null);
                        user.setBackgroundUrl(null);
                        em.merge(user);
                    }
                }
            } else {
                em.merge(user);
            }

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
    @CacheInvalidateAll(cacheName = "team-members")
    public void updateUserRole(Long userId, String role) {
        log.info("UserOrm/updateUserRole: userId=" + userId + " role=" + role);
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setRole(role);
            em.merge(user);
        }
    }

}

