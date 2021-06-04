package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.swing.text.AttributeSet.ColorAttribute;
import javax.ws.rs.InternalServerErrorException;
//Logging zeug
import org.jboss.logging.Logger;
//HTTP Requests
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

//Eigene Imports
import model.User;
import orm.UserOrm;
import javax.ws.rs.QueryParam;

//Emailzeug
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.reactive.ReactiveMailer;

//Sicherheits Zeug
import javax.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.jwt.JsonWebToken;
import java.security.Principal;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

@Path("/user")
// @RequestScoped
@ApplicationScoped
public class UserResouce {
    private static final Logger LOG = Logger.getLogger(UserResouce.class);

   
    @Inject
    UserOrm userOrm;

    // @Inject
    // @SecurityIdentity test;

    @Inject
    JsonWebToken  jwt;

    @GET
    // @RolesAllowed("user")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<User> getUser(@QueryParam("userId") Long userId, @QueryParam("username") String userName){
        LOG.info("UserResource/getUser");
        if (userId != null) {
            LOG.info("getUserById");
            return userOrm.getUserById(userId);
        } else if (userName != null) {
            LOG.info("getUserByUsername");
            return userOrm.getUserByUsername(userName);
        } else {
            LOG.info("getUsers");
            return userOrm.getUsers();
        }
    }

    @POST
    // @RolesAllowed("admin,user")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateUser(User user) {
        LOG.info("UserResource/updateUser");
        return userOrm.updateUser(user);
    }

    @POST
    @Path("/login")
    // @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String login(User user){
        return userOrm.loginUser(user);
    }

    @PUT
    // @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addUser(User usr) {
        LOG.info("UserResource/addUser");
        return userOrm.addUser(usr);
    }

    @DELETE
    // @RolesAllowed("admin,user")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteUser(User usr) {
        LOG.info("UserResource/deleteUser");
        // return userOrm.addUser(usr);
        return "testingdelete";
    }

    //Blocking sync
    // @Inject
    // Mailer mailer;

    //Async
    // @Inject
    // ReactiveMailer reactiveMailer;

    
    // mailer.send(Mail.withText("to@acme.org", "A simple email from quarkus", "This is my body."));

    // Uni<Void> stage = reactiveMailer.send(Mail.withText("to@acme.org", "A reactive email from quarkus", "This is my body."));

}
