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

    @GET
    @Path("/validateLogin")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response validateLogin(@CookieParam("jwt") String token){
        log.info("SecretResource/validateLogin");
        String key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAseIvpZEu6aqyaQz8JbnHsOuHMUH7jOcZ0DiJiVAD2vLdwJhet0RZoAKHCDqRd+1g+U0LIVMnLW2K04cmeOkkILAo+7uoj9v+rVvLoD9w91bNplZPG7wW9kbjliQaXJKkEhHMqwxdmRYLfcC6SdjkT3PrGisOIEPgP+24iZzoaDz+5i2JGYQyBmmcTZRBAiUNAKHRAT0cCW3dmCvYUde1/GoP4Oo7GPl0gGSQ60VhsTWB20dr4lXyMPNnU0K/3iBASuTlPdvWLjtflZQPA22Z7YFw3a4gHzrKZ/ZNBbfSwwwVSlqhxK8KwR1F2H0uOifZWCF5TURyME0cDYKG3G1BGwIDAQAB";
        JsonWebToken jwt;

        try {
            jwt = parser.parse(token);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return Response.status(500).build();
        }

        JsonWebToken isValid;
        try {
            isValid = parser.verify(token, key);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
            return Response.status(200).entity(e).build();
        }

        return Response.ok("isValid").build();
    }

}
