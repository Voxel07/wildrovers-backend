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
import tools.Email;
import tools.GeoIPService;
import tools.NotificationService;
import helper.AccountSnapshotCache;
import helper.RequestIpCapture;
import tools.Time;
import org.eclipse.microprofile.config.inject.ConfigProperty;

//Logging

//Coockie
import jakarta.ws.rs.core.Response;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import tools.Telemetry;

@ApplicationScoped
public class UserOrm {


    @Inject
    EntityManager em;

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

    @Inject
    NotificationService notificationService;

    @Inject
    AccountSnapshotCache accountSnapshots;

    /** Verified when the account does not exist, so a miss costs as much time as a wrong password. */
    private static final String DUMMY_PASSWORD_HASH = BcryptUtil.bcryptHash(java.util.UUID.randomUUID().toString());

    @ConfigProperty(name = "app.frontend-url", defaultValue = "http://localhost:5173")
    String frontendUrl;

    @Inject
    @io.quarkus.cache.CacheName("team-members")
    io.quarkus.cache.Cache teamMembersCache;

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

    public Long getForumPostCount(Long userId) {
        try {
            return em.createQuery("SELECT COUNT(fp) FROM ForumPost fp WHERE fp.creator.id = :userId", Long.class)
                    .setParameter("userId", userId).getSingleResult();
        } catch (Exception e) { return 0L; }
    }

    public Long getForumAnswerCount(Long userId) {
        try {
            return em.createQuery("SELECT COUNT(fa) FROM ForumAnswer fa WHERE fa.creator.id = :userId", Long.class)
                    .setParameter("userId", userId).getSingleResult();
        } catch (Exception e) { return 0L; }
    }

    public Long getForumTopicCount(Long userId) {
        try {
            return em.createQuery("SELECT COUNT(ft) FROM ForumTopic ft WHERE ft.creator.id = :userId", Long.class)
                    .setParameter("userId", userId).getSingleResult();
        } catch (Exception e) { return 0L; }
    }

    public Long getForumCategoryCount(Long userId) {
        try {
            return em.createQuery("SELECT COUNT(fc) FROM ForumCategory fc WHERE fc.creator.id = :userId", Long.class)
                    .setParameter("userId", userId).getSingleResult();
        } catch (Exception e) { return 0L; }
    }

    private void populateForumActivity(List<User> users) {
        if (users == null || users.isEmpty()) return;
        List<Long> userIds = users.stream().map(User::getId).toList();

        // Forum posts (not deleted)
        try {
            List<Object[]> counts = em.createQuery(
                    "SELECT fp.creator.id, COUNT(fp) FROM ForumPost fp WHERE fp.creator.id IN :userIds GROUP BY fp.creator.id",
                    Object[].class)
                    .setParameter("userIds", userIds)
                    .getResultList();
            java.util.Map<Long, Long> map = new java.util.HashMap<>();
            for (Object[] row : counts) map.put((Long) row[0], (Long) row[1]);
            for (User u : users) u.setForumPostCount(map.getOrDefault(u.getId(), 0L));
        } catch (Exception e) {
            Telemetry.warn("Failed to populate forum post counts", e);
        }

        // Forum answers (not deleted)
        try {
            List<Object[]> counts = em.createQuery(
                    "SELECT fa.creator.id, COUNT(fa) FROM ForumAnswer fa WHERE fa.creator.id IN :userIds GROUP BY fa.creator.id",
                    Object[].class)
                    .setParameter("userIds", userIds)
                    .getResultList();
            java.util.Map<Long, Long> map = new java.util.HashMap<>();
            for (Object[] row : counts) map.put((Long) row[0], (Long) row[1]);
            for (User u : users) u.setForumAnswerCount(map.getOrDefault(u.getId(), 0L));
        } catch (Exception e) {
            Telemetry.warn("Failed to populate forum answer counts", e);
        }

        // Forum topics (not deleted)
        try {
            List<Object[]> counts = em.createQuery(
                    "SELECT ft.creator.id, COUNT(ft) FROM ForumTopic ft WHERE ft.creator.id IN :userIds GROUP BY ft.creator.id",
                    Object[].class)
                    .setParameter("userIds", userIds)
                    .getResultList();
            java.util.Map<Long, Long> map = new java.util.HashMap<>();
            for (Object[] row : counts) map.put((Long) row[0], (Long) row[1]);
            for (User u : users) u.setForumTopicCount(map.getOrDefault(u.getId(), 0L));
        } catch (Exception e) {
            Telemetry.warn("Failed to populate forum topic counts", e);
        }

        // Forum categories (not deleted)
        try {
            List<Object[]> counts = em.createQuery(
                    "SELECT fc.creator.id, COUNT(fc) FROM ForumCategory fc WHERE fc.creator.id IN :userIds GROUP BY fc.creator.id",
                    Object[].class)
                    .setParameter("userIds", userIds)
                    .getResultList();
            java.util.Map<Long, Long> map = new java.util.HashMap<>();
            for (Object[] row : counts) map.put((Long) row[0], (Long) row[1]);
            for (User u : users) u.setForumCategoryCount(map.getOrDefault(u.getId(), 0L));
        } catch (Exception e) {
            Telemetry.warn("Failed to populate forum category counts", e);
        }

        // Last forum activity (most recent post or answer creationDate)
        try {
            java.util.Map<Long, Long> lastActivity = new java.util.HashMap<>();
            List<Object[]> postMax = em.createQuery(
                    "SELECT fp.creator.id, MAX(fp.creationDate) FROM ForumPost fp WHERE fp.creator.id IN :userIds GROUP BY fp.creator.id",
                    Object[].class).setParameter("userIds", userIds).getResultList();
            for (Object[] row : postMax) {
                Long uid = (Long) row[0];
                Long ts = (Long) row[1];
                lastActivity.merge(uid, ts, Math::max);
            }
            List<Object[]> answerMax = em.createQuery(
                    "SELECT fa.creator.id, MAX(fa.creationDate) FROM ForumAnswer fa WHERE fa.creator.id IN :userIds GROUP BY fa.creator.id",
                    Object[].class).setParameter("userIds", userIds).getResultList();
            for (Object[] row : answerMax) {
                Long uid = (Long) row[0];
                Long ts = (Long) row[1];
                lastActivity.merge(uid, ts, Math::max);
            }
            for (User u : users) u.setLastForumActivity(lastActivity.get(u.getId()));
        } catch (Exception e) {
            Telemetry.warn("Failed to populate last forum activity", e);
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
            Telemetry.warn("Failed to populate events attended count", e);
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
            Telemetry.warn("Failed to populate yearly fees", e);
        }
    }

    @WithSpan
    public List<User> getUsers() {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        List<User> users = query.getResultList();
        populateEventsAttended(users);
        populateYearlyFees(users);
        populateForumActivity(users);
        return users;
    }

    @WithSpan
    public List<User> getUserById(Long userId) {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE id =: val", User.class);
        query.setParameter("val", userId);
        List<User> users = query.getResultList();
        populateEventsAttended(users);
        populateYearlyFees(users);
        populateForumActivity(users);
        return users;
    }

    @WithSpan
    public List<User> getUserByUsername(String userName) {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE userName =: val", User.class);
        query.setParameter("val", userName);
        List<User> users = query.getResultList();
        populateYearlyFees(users);
        return users;
    }

    @WithSpan
    public User findByUsername(String username) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.userName = :val", User.class)
                    .setParameter("val", username)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        } catch (RuntimeException e) {
            Telemetry.warn("User lookup by username failed", e);
            throw e;
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

    public User findById(Long id) {
        return em.find(User.class, id);
    }

    public User findByOidcIdentity(String issuer, String subject) {
        List<User> users = em.createQuery(
                "SELECT u FROM User u WHERE u.oidcIssuer = :issuer AND u.oidcSubject = :subject", User.class)
                .setParameter("issuer", issuer)
                .setParameter("subject", subject)
                .getResultList();
        return users.isEmpty() ? null : users.get(0);
    }

    /** Claims of an OIDC access token relevant for account binding. */
    public record OidcProfile(String issuer, String subject, String preferredUsername, String email,
            boolean emailVerified, String firstName, String lastName) { }

    /** Either the bound account or the reason why no account may be used. */
    public record OidcResolution(User user, String problem) { }

    /**
     * Resolves the account bound to an OIDC identity (issuer + subject).
     * <ul>
     *   <li>A bound account is selected only by issuer + subject, never by mutable claims.</li>
     *   <li>An unbound account with the same e-mail is linked only if the identity provider
     *       asserts {@code email_verified} and the account is not bound to another subject.</li>
     *   <li>Otherwise a new account is provisioned with a unique username; an existing
     *       username is never taken over.</li>
     * </ul>
     * The application role is synchronised from the identity provider's group mapping.
     */
    @Transactional
    @WithSpan
    public OidcResolution resolveOidcUser(OidcProfile profile, String mappedRole) {
        String role = mappedRole != null ? mappedRole : model.Users.Roles.VSISITOR;
        boolean changed = false;
        User user = findByOidcIdentity(profile.issuer(), profile.subject());

        if (user == null) {
            String email = profile.email() != null ? profile.email().trim() : null;
            if (email == null || email.isBlank()) {
                return new OidcResolution(null,
                        "Dein Anmeldekonto liefert keine E-Mail-Adresse. Bitte wende dich an den Vorstand.");
            }
            User existing = findByEmail(email);
            if (existing != null) {
                if (existing.getOidcSubject() != null || !profile.emailVerified()) {
                    Telemetry.warn("OIDC identity cannot be linked to account (already bound or e-mail not verified)",
                            "user.id", existing.getId(), "oidc.issuer", profile.issuer(), "oidc.email_verified", profile.emailVerified());
                    return new OidcResolution(null,
                            "Dieses Anmeldekonto kann keinem Benutzer zugeordnet werden. Bitte wende dich an den Vorstand.");
                }
                existing.setOidcIssuer(profile.issuer());
                existing.setOidcSubject(profile.subject());
                user = existing;
                Telemetry.event("oidc.account_linked", "user.id", existing.getId(), "oidc.issuer", profile.issuer());
            } else {
                user = createOidcUser(profile, email, role);
                changed = true;
            }
        }

        if (!role.equals(user.getRole())) {
            Telemetry.event("oidc.role_synced", "user.id", user.getId(), "user.role.previous", user.getRole(), "user.role", role);
            user.setRole(role);
            changed = true;
        }
        if (changed) {
            teamMembersCache.invalidateAll().await().indefinitely();
            accountSnapshots.invalidate(user.getId());
        }
        return new OidcResolution(user, null);
    }

    private User createOidcUser(OidcProfile profile, String email, String role) {
        String username = uniqueUsername(profile.preferredUsername(), profile.subject());
        Telemetry.event("oidc.account_created", "oidc.issuer", profile.issuer(), "user.role", role);
        User usr = new User();
        usr.setUserName(username);
        usr.setEmail(email);
        usr.setFirstName(profile.firstName() != null && !profile.firstName().isBlank() ? profile.firstName() : "OIDC");
        usr.setLastName(profile.lastName() != null && !profile.lastName().isBlank() ? profile.lastName() : "User");
        usr.setRole(role);
        usr.setActive(true);
        usr.setOidcIssuer(profile.issuer());
        usr.setOidcSubject(profile.subject());
        // Local password login stays impossible until the user explicitly resets a password.
        usr.setPassword(BcryptUtil.bcryptHash(java.util.UUID.randomUUID().toString()));
        usr.setRegDate(Time.currentTimeInMillis());
        em.persist(usr);

        // Register secret for user, marked verified since they authenticated via OIDC
        String verificationId = secretOrm.generateVerificationId();
        String dummyHash = BcryptUtil.bcryptHash(java.util.UUID.randomUUID().toString());
        secretOrm.addSecret(usr.getId(), true, verificationId, dummyHash);

        recordSignupNotification(usr);
        return usr;
    }

    private String uniqueUsername(String preferred, String subject) {
        String base = preferred != null ? preferred.trim() : "";
        if (base.isBlank()) {
            base = "oidc-" + Integer.toHexString(subject.hashCode());
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String candidate = base;
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int attempt = 0; attempt < 20 && findByUsername(candidate) != null; attempt++) {
            candidate = base + "-" + (1000 + random.nextInt(9000));
        }
        if (findByUsername(candidate) != null) {
            candidate = base + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        return candidate;
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
    @WithSpan
    public Response addUser(model.Users.SignupRequest signup) {
        String userName = signup.userName.trim();
        String mail = signup.email.trim();

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2",
                User.class);
        query.setParameter("val1", userName);
        query.setParameter("val2", mail);

        if (!query.getResultList().isEmpty()) {
            return Response.status(406).entity("Nutzer bereits bekannt").build();
        }

        // Build the entity from the allowlisted signup fields only. Role,
        // permissions and status are never taken from the request.
        String passwordHash = BcryptUtil.bcryptHash(signup.password);
        User usr = new User();
        usr.setUserName(userName);
        usr.setEmail(mail);
        usr.setFirstName(signup.firstName.trim());
        usr.setLastName(signup.lastName.trim());
        usr.setPassword(passwordHash);
        usr.setRegDate(Time.currentTimeInMillis());
        usr.setActive(true);
        usr.setIsBlocked(false);
        usr.setCanCreateCategory(false);
        usr.setRole(model.Users.Roles.VSISITOR);

        try {
            em.persist(usr);
        } catch (Exception e) {
            Telemetry.error("Inserting user failed", e);
            return Response.status(500).entity("Fehler beim Nutzer einfügen").build();
        }

        // NOTE:
        /**
         * Needs to be after the User has been persisted to the Database so we can get
         * the ID;
         */
        /*
         * Generate secrets
         */
        Long userId = usr.getId();
        String verificationId = secretOrm.generateVerificationId();
        secretOrm.addSecret(userId, false, verificationId, passwordHash);
        recordSignupNotification(usr);
        // Id zurückgeben

        /**
         * Send Email so that the user can verify his acc
         */
        try {
            email.sendVerificationMail(usr.getEmail(), verificationId);
        } catch (Exception e) {
            Telemetry.error("Sending verification e-mail failed", e);
            throw new jakarta.ws.rs.WebApplicationException(
                Response.status(500)
                    .entity("Fehler beim Senden der Verifizierungs-E-Mail: " + e.getMessage())
                    .build()
            );
        }

        return Response.status(201).entity("Nutzer erfolgreich erstellt").build();

    }

    private void recordSignupNotification(User user) {
        try {
            notificationService.recordSignup(user, frontendUrl);
        } catch (Exception e) {
            Telemetry.warn("Could not record signup notification", e, "user.id", user.getId());
        }
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
    @WithSpan
    public Response updateUser(User u) {
        User dbUser = em.find(User.class, u.getId());
        if (dbUser == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Keinen Nutzer mit diesen Daten gefunden").build();
        }
        if (u.getUserName() == null || u.getUserName().isBlank() || u.getEmail() == null || u.getEmail().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Benutzername und E-Mail müssen gesetzt sein").build();
        }

        List<User> conflicts = em.createQuery(
                "SELECT u FROM User u WHERE (u.userName = :userName OR u.email = :email) AND u.id <> :id", User.class)
                .setParameter("userName", u.getUserName())
                .setParameter("email", u.getEmail())
                .setParameter("id", u.getId())
                .getResultList();
        for (User other : conflicts) {
            String message = u.getUserName().equals(other.getUserName())
                    ? "Username bereits vergeben" : "Email bereits vergeben";
            return Response.status(Response.Status.CONFLICT).entity(message).build();
        }

        // Authentik owns the role of linked accounts and would overwrite a change made here at the next login.
        if (dbUser.isOidcLinked() && u.getRole() != null && !u.getRole().isBlank()
                && !u.getRole().equals(dbUser.getRole())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Die Rolle dieses Benutzers wird über Authentik-Gruppen verwaltet und kann hier nicht geändert werden.")
                    .build();
        }
        if (u.getRole() != null && !u.getRole().isBlank() && !model.Users.Roles.getRoles().contains(u.getRole())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unbekannte Rolle").build();
        }

        if (!u.getUserName().equals(dbUser.getUserName())) {
            forumCategoryOrm.invalidateCategoryCaches();
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
                    dbUser.revokeTokens();
                }
            }
        }
        accountSnapshots.invalidate(dbUser.getId());
        return Response.ok(jakarta.json.Json.createObjectBuilder()
                .add("status", "success")
                .add("message", "User erfolgreich aktualisiert")
                .build()).build();
    }

    /** Outcome of a password login: either the account or the error response to return. */
    public record LoginResult(User user, Response error) { }

    @Transactional
    @WithSpan
    public LoginResult authenticateLocal(User usr) {
        if (usr == null || (usr.getUserName() == null && usr.getEmail() == null) || usr.getPassword() == null) {
            return new LoginResult(null, Response.status(Response.Status.BAD_REQUEST)
                    .entity("Bitte Benutzername/E-Mail und Passwort eingeben.").build());
        }

        List<User> found = em.createQuery("SELECT u FROM User u WHERE u.userName = :val OR u.email = :val", User.class)
                .setParameter("val", usr.getUserName() != null ? usr.getUserName() : usr.getEmail())
                .setMaxResults(1)
                .getResultList();
        User user = found.isEmpty() ? null : found.get(0);

        // Always verify a password hash before revealing anything: unknown accounts are
        // checked against a dummy hash so the response time does not reveal them, and
        // account state is only disclosed to callers who know the password.
        String storedPasswordHash = user != null && user.getSecret() != null ? user.getSecret().getPassword() : null;
        boolean passwordMatches;
        try {
            passwordMatches = verifyBCryptPassword(
                    storedPasswordHash != null ? storedPasswordHash : DUMMY_PASSWORD_HASH, usr.getPassword())
                    && storedPasswordHash != null;
        } catch (Exception e) {
            Telemetry.error("Password verification failed", e);
            passwordMatches = false;
        }
        if (!passwordMatches) {
            return new LoginResult(null, invalidCredentialsResponse());
        }

        if (!Boolean.TRUE.equals(user.getSecret().getIsVerifyed())) {
            JsonObject response = jakarta.json.Json.createObjectBuilder()
                    .add("code", "ACCOUNT_NOT_VERIFIED")
                    .add("message", "Bitte verifiziere dein Konto")
                    .build();
            return new LoginResult(null, Response.status(401).entity(response).build());
        }

        if (!helper.AccountStatus.isUsable(user)) {
            return new LoginResult(null, Response.status(403)
                    .header(helper.AccountStatus.STATUS_HEADER, helper.AccountStatus.STATUS_DENIED)
                    .entity(helper.AccountStatus.BLOCKED_MESSAGE).build());
        }

        recordLogin(user);
        return new LoginResult(user, null);
    }

    /** Stores login time, IP and (when a local GeoIP database is configured) country. */
    @Transactional
    public void recordLogin(User user) {
        User managed = em.contains(user) ? user : em.find(User.class, user.getId());
        if (managed == null) return;
        managed.setLastLogin(Time.currentTimeInMillis());
        String clientIp = ipCapture.getClientIp();
        managed.setLastLoginIp(clientIp);
        if (clientIp != null && !"unknown".equals(clientIp)) {
            managed.setLastLoginCountry(geoIPService.getCountry(clientIp));
        }
    }

    @Transactional
    @WithSpan
    public Response resendVerification(User credentials) {
        if (credentials == null || credentials.getPassword() == null
                || (credentials.getUserName() == null && credentials.getEmail() == null)) {
            return invalidCredentialsResponse();
        }

        String identifier = credentials.getUserName() != null
                ? credentials.getUserName().trim()
                : credentials.getEmail().trim();
        if (identifier.isBlank() || credentials.getPassword().isBlank()) {
            return invalidCredentialsResponse();
        }

        User user;
        try {
            user = em.createQuery(
                    "SELECT u FROM User u WHERE u.userName = :identifier OR u.email = :identifier", User.class)
                    .setParameter("identifier", identifier)
                    .getSingleResult();
        } catch (Exception e) {
            return invalidCredentialsResponse();
        }

        try {
            String storedPasswordHash = user.getSecret() != null ? user.getSecret().getPassword() : null;
            if (storedPasswordHash == null
                    || !verifyBCryptPassword(storedPasswordHash, credentials.getPassword())) {
                return invalidCredentialsResponse();
            }
        } catch (Exception e) {
            Telemetry.event("verification.resend_rejected");
            return invalidCredentialsResponse();
        }

        if (Boolean.TRUE.equals(user.getSecret().getIsVerifyed())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Dieses Konto ist bereits verifiziert").build();
        }

        String verificationId = secretOrm.generateVerificationId();
        user.getSecret().setVerificationId(verificationId);
        user.getSecret().setVerificationTimestamp(Time.currentTimeInMillis());
        em.merge(user.getSecret());

        // The recipient always comes from the authenticated account record, never
        // from a caller-controlled email address.
        email.sendVerificationMail(user.getEmail(), verificationId);
        return Response.ok(jakarta.json.Json.createObjectBuilder()
                .add("message", "Ein neuer Verifizierungscode wurde gesendet.")
                .build()).build();
    }

    private Response invalidCredentialsResponse() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Benutzername oder Passwort falsch").build();
    }

    /**
     * Ends every session of the account on every device: access tokens carry the
     * token version, refresh sessions are checked against it.
     */
    @Transactional
    public void revokeAllSessions(Long userId) {
        User user = userId != null ? em.find(User.class, userId) : null;
        if (user != null) {
            user.revokeTokens();
            accountSnapshots.invalidate(userId);
        }
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
    @WithSpan
    public List<User> getTeamMembers() {
        TypedQuery<User> query = em.createQuery(
                "SELECT u FROM User u WHERE u.role IN ('Admin', 'Vorstand', 'Mitglied', 'Frischling') AND u.isActive = true",
                User.class);
        List<User> users = query.getResultList();
        populateYearlyFees(users);
        return users;
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
    @WithSpan
    public User updateUserProfile(Long userId, String phrase, java.time.LocalDate birthday, String firstName,
            String lastName, String email, String userName) {
        User user = em.find(User.class, userId);
        if (user != null) {
            if (userName != null && !userName.isBlank()) {
                User existing = findByUsername(userName);
                if (existing != null && !existing.getId().equals(userId)) {
                    throw new IllegalArgumentException(
                            "Dieser Benutzername wird bereits verwendet.");
                }
                if (!userName.equals(user.getUserName())) {
                    try {
                        forumCategoryOrm.invalidateCategoryCaches();
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Fehler beim Invalidate der Kategorien-Caches.");
                    }
                }
                user.setUserName(userName);
            }
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
    @WithSpan
    public void updateUserPhotoUrl(Long userId, String photoUrl) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setPhotoUrl(photoUrl);
            em.merge(user);
        }
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
    @WithSpan
    public void updateUserBackgroundUrl(Long userId, String backgroundUrl) {
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
    @WithSpan
    public Response deleteUserWithOptions(Long userId, boolean deleteAccount, boolean deleteEvents, boolean deletePosts, boolean deleteGallery, boolean hardDelete) {
        accountSnapshots.invalidate(userId);
        Telemetry.attrs("user.id", userId, "user.delete.account", deleteAccount, "user.delete.events", deleteEvents,
                "user.delete.posts", deletePosts, "user.delete.gallery", deleteGallery, "user.delete.hard", hardDelete);
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
                // 1. Delete answers by this user
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
                    em.createNativeQuery("DELETE FROM FORUM_ANSWERS WHERE post_id IN (:pids)")
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
                    Telemetry.warn("Failed to decrement user poll votes", e);
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
            Telemetry.error("Deleting user failed", e, "user.id", userId);
            return Response.status(500).entity("Fehler beim Löschen des Benutzers: " + e.getMessage()).build();
        }
    }


    /**
     * Targeted role-only update — avoids going through the full updateUser() path
     * which could inadvertently trigger password re-hashing.
     */
    @Transactional
    @CacheInvalidateAll(cacheName = "team-members")
    @WithSpan
    public void updateUserRole(Long userId, String role) {
        Telemetry.attrs("user.id", userId, "user.role", role);
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setRole(role);
            em.merge(user);
            accountSnapshots.invalidate(userId);
        }
    }

}

