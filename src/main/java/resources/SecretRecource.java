package resources;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

//Logging zeug
import java.util.logging.Logger;

//HTTP Requests
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
//Eigene Imports
import orm.Secrets.SecretOrm;

@Path("/secrets")
@ApplicationScoped
public class SecretRecource {
    private static final Logger log = Logger.getLogger(SecretRecource.class.getName());

    @Inject SecretOrm secretOrm;
    @Inject JWTParser parser;

    @GET
    @Path("/verify")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String verifyUser(@QueryParam("userId") Long userId, @QueryParam("verificationId") String verificationId){
        log.info("SecretResource/verifyUser");

        /**
         * Prevent verification spam here.
         * Maybe set a timeout if it failes
         */
        return secretOrm.verifyUser(userId, verificationId);
    }

}
