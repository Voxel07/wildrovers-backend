package orm;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.print.DocFlavor.STRING;
import javax.transaction.Transactional;

import io.netty.handler.codec.http.cookie.CookieHeaderNames.SameSite;
import io.quarkus.elytron.security.common.BcryptUtil;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.ResponseBuilder;


import model.User;
import orm.Forum.ForumCategoryOrm;
import orm.Secrets.SecretOrm;
import orm.UserStuff.ActivityForumOrm;
import tools.Email;
import resources.GenerateToken;
import helper.CustomHttpResponse;
import java.time.LocalDate;

//Logging
import java.util.logging.Logger;
import java.util.logging.Level;

//Coockie
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@ApplicationScoped
public class UserOrm
{

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

    public List<User> getUsers()
    {
        log.info("UserOrm/getUsers");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    public List<User> getUserById(Long userId)
    {
        log.info("UserOrm/getUserById");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE id =: val", User.class);
        query.setParameter("val", userId);
        return query.getResultList();
    }

    public List<User> getUserByUsername(String userName)
    {
        log.info("UserOrm/getUserByUsername");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE userName =: val", User.class);
        query.setParameter("val", userName);
        return query.getResultList();
    }

    @Transactional
    public String addUser(User usr)
    {
        log.info("UserOrm/addUser");

        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val2", User.class);
        query.setParameter("val1", usr.getUserName());
        query.setParameter("val2", usr.getEmail());

        if (!query.getResultList().isEmpty())
        {
            return "Nutzer bereits bekannt";
        }

        usr.setPassword(BcryptUtil.bcryptHash(usr.getPassword()));
        usr.setRegDate(LocalDate.now());
        usr.setActive(true);

        // Nutzer einfügen
        try
        {
            em.persist(usr);
        }
        catch (Exception e)
        {
            return "Fehler beim Nutzer einfügen" + e;
        }

        //NOTE:
        /**
         * Needs to be after the User has been persisted to the Database so we can get the ID;
         */
        //create Activity logs
        Long userId = usr.getId();
        activityForumOrm.addActivityForum(userId);

        /*
         * Generate Secreats
         */
        String verificationId =  secretOrm.generateVerificationId();
        secretOrm.addSecret(userId, false, verificationId);
        // Id zurückgeben

        /**
         * Send Email so that the user can verify his acc
         */
        email.sendVerificationMail(usr.getEmail(), userId, verificationId);

        return "" + getUserByUsername(usr.getUserName()).get(0).getId();
    }

    @Transactional
    public String updateUser(User u)
    {
        log.info("UserOrm/updateUser");

        boolean error = false;
        String errorMSG = "";
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: UserName OR u.email =: Email OR u.id =: Id", User.class); //All values are needed to detect dublicate

        query.setParameter("UserName", u.getUserName());
        query.setParameter("Email", u.getEmail());
        query.setParameter("Id", u.getId());

        List<User> userAusDB = query.getResultList();
        if(u.getPassword() != null){
            u.setPassword(BcryptUtil.bcryptHash(u.getPassword())); //hash the updated Password
        }

        // Wenn user zurückgekomen sind
        if (userAusDB == null) return "Keinen Nutzer mit diesen Daten gefunden";
        // Check all user to return a the specific reason.
        // NOTE: short to  userAusDB.size() != 1 ?
        for (User aktUser : userAusDB)
        {
            // Überprüfen ob die ID die gleiche ist.
            if (!aktUser.getId().equals(u.getId()) && !error)
            {
                if (aktUser.getUserName().equals(u.getUserName()))
                {
                    error = true;
                    errorMSG = "Username bereits vergeben";
                }
                // Oder die Email
                else if (aktUser.getEmail().equals(u.getEmail()))
                {
                    error = true;
                    errorMSG = "Email bereits vergeben";
                }
            }
        }

        if (error) return errorMSG; //Return the specific reason.

        //No unique values are harmed, so the first and only entry in the list has to be the correct one.
        if(!u.getUserName().equals(userAusDB.get(0).getUserName())){
            try {
                forumCategoryOrm.updateCategoryUserName(u.getUserName(), userAusDB.get(0).getUserName());
            } catch (Exception e) {

                return"Fehler beim Updaten der Kategorien"+ e.toString();
            }
            //TODO: Update Topics, Posts, Answers as well
        }

        try
        {
            em.merge(u);
            errorMSG = "User erfolgreich aktualisiert";
        }
        catch (Exception e)
        {
            errorMSG = "Fehler beim Updaten des User";
        }


        return errorMSG;
    }

    public Response loginUser(User usr)
    {
        log.info("UserOrm/loginUser");

        // try {
        //     log.info("Waiting 10 sec");

        //     Thread.sleep(3000);
        // } catch (InterruptedException e1) {
        //     // TODO Auto-generated catch block
        //     e1.printStackTrace();
        // }
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u WHERE u.userName =: val1 OR u.email =: val1", User.class);
        query.setParameter("val1", usr.getUserName());
        // query.setParameter("val2", usr.getEmail());

        //Find user in DB
        User user;
        try
        {
            user = query.getSingleResult();
        }
        catch (Exception e)
        {
            log.info("Kein user gefunden");
            //TODO: Change text to: "Benutzername oder Password flasch"
            return Response.status(401).entity("Benutzer nicht gefunden").build();
        }
        //Verify Password
        try
        {
            if(!verifyBCryptPassword(user.getPassword(), usr.getPassword()))
            {
                log.info("Falsches PW");
            //TODO: Change text to: "Benutzername oder Password flasch"
                return Response.status(401).entity("Falsches Password").build();

            }
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Result{0}", e.getMessage());
            return Response.status(401).entity("Fehler bei der Passwortprüfung").build();
        }

        //Return cookie

        return generateCookie(user);
    }

    public Response generateCookie(User user){
        log.info("UserOrm/generateCookie");

        String token = GenerateToken.generator(user.getRole(),user.getUserName());
        // return token;
         return Response.ok(token, MediaType.TEXT_PLAIN_TYPE)
         .header("SetCookie", "jwt" + token + "; SameSite=strict")
         // set the Expires response header to two days from now
        //  .expires(Date.from(Instant.now().plus(Duration.ofDays(2))))
         // send a new cookie
        //  .cookie(new NewCookie("JWT", token, "hallo","wildwovers.wtf","test",3600,true,true))
         // end of builder API
         .build();
    }

    public static boolean verifyBCryptPassword(String bCryptPasswordHash, String passwordToVerify)
    throws Exception
    {

        WildFlyElytronPasswordProvider provider = new WildFlyElytronPasswordProvider();

        // 1. Create a BCrypt Password Factory
        PasswordFactory passwordFactory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT, provider);

        // 2. Decode the hashed user password
        Password userPasswordDecoded = ModularCrypt.decode(bCryptPasswordHash);

        // 3. Translate the decoded user password object to one which is consumable by this factory.
        Password userPasswordRestored = passwordFactory.translate(userPasswordDecoded);

        // Verify existing user password you want to verify
        return passwordFactory.verify(userPasswordRestored, passwordToVerify.toCharArray());
    }

}
