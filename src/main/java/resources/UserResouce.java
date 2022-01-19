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

//Eigene Imports
import model.User;
import orm.UserOrm;
import javax.ws.rs.QueryParam;

//Coockie
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;


//Sicherheits Zeug
import javax.ws.rs.core.Context;
import org.eclipse.microprofile.jwt.JsonWebToken;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import io.vertx.core.http.HttpServerRequest;

@Path("/user")
@RequestScoped
// @ApplicationScoped
public class UserResouce 
{
    private static final Logger log = Logger.getLogger(UserResouce.class.getName());

    @Inject
    UserOrm userOrm;

    @Inject
    JsonWebToken jwt;

    @Context
    UriInfo info;

    @Context
    HttpServerRequest request;

    @Context
    HttpHeaders header;

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
    @RolesAllowed("admin,user")
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
        log.info("Info: " + info.getQueryParameters() + "|" + info.getBaseUri() + "|" + info.getPath() + "|" + info.getAbsolutePath());
        log.info("Request: " + request + "|" + request.host()+ "|" + request.cookieCount()+ "|" + request.remoteAddress()+ "|" + request.localAddress()+ "|" + request.method());

        if (Boolean.TRUE.equals(userOrm.loginUser(user)))
        {
            String token = GenerateToken.generator("user","camo");
            return Response.ok(token, MediaType.TEXT_PLAIN_TYPE)
            // set the Expires response header to two days from now
            .expires(Date.from(Instant.now().plus(Duration.ofDays(2))))
            // send a new cookie
            .cookie(new NewCookie("JWT", token))
            // end of builder API
            .build();
        }
        else
        {
            return Response.status(401).build();
        }
    }

    @PUT
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addUser(User usr) 
    {
        log.info("UserResource/addUser");
        return userOrm.addUser(usr);
    }

    @DELETE
    @RolesAllowed("admin,user")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteUser(User usr) 
    {
        log.info("UserResource/deleteUser");
        // return userOrm.addUser(usr);
        return "testingdelete";
    }
}
