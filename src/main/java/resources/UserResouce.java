package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

//Logging zeug
import java.util.logging.Logger;

//HTTP Requests
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import model.User;
import orm.UserOrm;
import jakarta.ws.rs.QueryParam;

//Sicherheits Zeug
import jakarta.ws.rs.core.Context;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import io.vertx.core.http.HttpServerRequest;

//Validator
import jakarta.validation.Valid;
import jakarta.validation.Validator;

@Path("/user")
@RequestScoped
// @ApplicationScoped
public class UserResouce
{
    private static final Logger log = Logger.getLogger(UserResouce.class.getName());

    @Inject UserOrm userOrm;

    @Inject helper.UserPrincipalResolver userPrincipalResolver;

    @Context UriInfo info;

    @Context HttpServerRequest request;

    @Context HttpHeaders header;

    @Inject Validator validator;

    @GET
    @Path("/me")
    @RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMe()
    {
        log.info("UserResource/getMe");
        User user = userPrincipalResolver.resolveUser();
        if (user == null)
        {
            return Response.status(401).entity("Benutzer nicht eingeloggt oder unbekannt").build();
        }
        return Response.ok(user).build();
    }

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
