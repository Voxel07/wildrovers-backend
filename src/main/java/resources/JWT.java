package resources;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.microprofile.jwt.Claims;

import io.smallrye.jwt.algorithm.KeyEncryptionAlgorithm;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import io.vertx.core.http.Cookie;
import model.User;

import javax.json.JsonObject;
import javax.ws.rs.core.NewCookie;
import javax.json.Json;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import java.time.Duration;
import java.time.Instant;

public class JWT {
    /**
     * Generate JWT token
     */
    public static String generator(String role , Long userId) {

      return Jwt
            .issuer("wildrovers") //needs to match application properties
            .upn(Long.toString(userId))
            .groups(new HashSet<>(Arrays.asList(role)))
            .expiresAt(Instant.now().plus(Duration.ofDays(30)))
            .jws()
            .algorithm(SignatureAlgorithm.RS256)
            .sign();
    }

    /**
     * Verify JWT token
     */
    public static boolean validator(String jwt){
      return true;
      // String key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAseIvpZEu6aqyaQz8JbnHsOuHMUH7jOcZ0DiJiVAD2vLdwJhet0RZoAKHCDqRd+1g+U0LIVMnLW2K04cmeOkkILAo+7uoj9v+rVvLoD9w91bNplZPG7wW9kbjliQaXJKkEhHMqwxdmRYLfcC6SdjkT3PrGisOIEPgP+24iZzoaDz+5i2JGYQyBmmcTZRBAiUNAKHRAT0cCW3dmCvYUde1/GoP4Oo7GPl0gGSQ60VhsTWB20dr4lXyMPNnU0K/3iBASuTlPdvWLjtflZQPA22Z7YFw3a4gHzrKZ/ZNBbfSwwwVSlqhxK8KwR1F2H0uOifZWCF5TURyME0cDYKG3G1BGwIDAQAB";
      // JsonWebToken jwt;

      // try {
      //     jwt = parser.parse(token);
      // } catch (ParseException e) {
      //     // TODO Auto-generated catch block
      //     e.printStackTrace();
      //     return Response.status(500).build();
      // }

      // JsonWebToken isValid;
      // try {
      //     isValid = parser.verify(token, key);
      // } catch (ParseException e) {
      //     // TODO Auto-generated catch block
      //     // e.printStackTrace();
      //     return Response.status(200).entity(e).build();
      // }

      // return Response.ok("isValid").build();

    }

    public static JsonObject createReactAuthObject(String token, User user){
      return Json.createObjectBuilder()
      .add("JWT", token)
      .add("USER", Json.createObjectBuilder()
              .add("Name", user.getUserName())
              .add("Role", user.getRole()).build()
              )
      .build();
    }

    public static NewCookie generateCookie(String token){
      return new NewCookie("__refresh_Token__", token, "/","localhost","test",3600*24*30,true,true);
    }

    public static NewCookie removeCookie(){
      return new NewCookie("__refresh_Token__", "deleted", "/","localhost:3000","test",0,true,true);
    }

}