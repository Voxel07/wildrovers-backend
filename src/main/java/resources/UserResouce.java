package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

//Logging zeug
import java.util.logging.Logger;

//HTTP Requests
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

//Eigene Imports
import model.User;
import orm.UserOrm;
import javax.ws.rs.QueryParam;

//Sicherheits Zeug
import javax.ws.rs.core.Context;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import io.vertx.core.http.HttpServerRequest;

//Validator
import javax.validation.Valid;
import javax.validation.Validator;

@Path("/user")
@RequestScoped
// @ApplicationScoped
public class UserResouce
{
    private static final Logger log = Logger.getLogger(UserResouce.class.getName());

    @Inject UserOrm userOrm;

    @Context UriInfo info;

    @Context HttpServerRequest request;

    @Context HttpHeaders header;

    @Inject Validator validator;

    @GET
    @RolesAllowed("user")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<User> getUser(@QueryParam("userId") Long userId, @QueryParam("username") String userName)
    {
        log.info("UserResource/getUser");
        if (userId != null)
        {
            log.info("getUserById");
            return userOrm.getUserById(userId);
        }
        else if (userName != null)
        {
            log.info("getUserByUsername");
            return userOrm.getUserByUsername(userName);
        }
        else
        {
            log.info("getUsers");
            return userOrm.getUsers();
        }
    }

    @POST
    // @RolesAllowed("admin,user")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateUser(User user)
    {
        log.info("UserResource/updateUser");
        return userOrm.updateUser(user);
    }

    @POST
    @Path("/login")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(User user)
    {
        log.info("UserResource/login");
        return userOrm.loginUser(user);
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response logout()
    {
        log.info("UserResource/logout");
        return userOrm.logoutUser();
    }

    @PUT
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addUser(@Valid User usr)
    {
        log.info("UserResource/addUser");
        return userOrm.addUser(usr);
    }

    @DELETE
    @RolesAllowed({ "user", "admin" })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteUser(User usr)
    {
        log.info("UserResource/deleteUser");
        // return userOrm.addUser(usr);
        return "testingdelete";
    }
}
