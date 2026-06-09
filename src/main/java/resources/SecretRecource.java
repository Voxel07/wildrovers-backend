package resources;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

//Logging zeug
import java.util.logging.Logger;

//HTTP Requests
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
    @Inject orm.UserOrm userOrm;
    @Inject jakarta.persistence.EntityManager em;

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

    @GET
    @Path("/auto-verify")
    @Produces(MediaType.APPLICATION_JSON)
    @jakarta.transaction.Transactional
    public String autoVerify(@QueryParam("username") String username) {
        log.info("SecretResource/autoVerify: " + username);
        try {
            model.User user = userOrm.findByUsername(username);
            if (user != null && user.getSecret() != null) {
                user.getSecret().setIsVerifyed(true);
                em.merge(user.getSecret());
                return "{\"status\":\"Auto-verified " + username + "\"}";
            }
        } catch (Exception e) {
            return "{\"status\":\"Error: " + e.getMessage() + "\"}";
        }
        return "{\"status\":\"User not found\"}";
    }
}
