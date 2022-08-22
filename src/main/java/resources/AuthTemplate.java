package resources;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.CookieParam;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
@Path("/secured")
@RequestScoped
public class AuthTemplate {

    @Inject
    JsonWebToken jwt;

    @Inject
    @Claim(standard = Claims.birthdate)
    String birthdate;

    @Inject JWTParser parser;

    @GET
    @Path("permit-all")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(@Context SecurityContext ctx) {
    	return getResponseString(ctx);
    }

    @GET
    @Path("roles-allowed")
    @RolesAllowed({ "user", "admin" })
    @Produces(MediaType.TEXT_PLAIN)
    public String helloRolesAllowed(@Context SecurityContext ctx) {
    	return getResponseString(ctx) + ", birthdate: " + jwt.getClaim("birthdate").toString()+ "role";
    }

    @GET
    @Path("roles-allowed-admin")
    @RolesAllowed("admin")
    @Produces(MediaType.TEXT_PLAIN)
    public String helloRolesAllowedAdmin(@Context SecurityContext ctx) {
    	return getResponseString(ctx) + ", birthdate: " + birthdate;
    }

    @GET
    @Path("deny-all")
    @DenyAll
    @Produces(MediaType.TEXT_PLAIN)
    public String helloShouldDeny(@Context SecurityContext ctx) {
        throw new InternalServerErrorException("This method must not be invoked");
    }

    private String getResponseString(SecurityContext ctx) {
    	String name;
        if (ctx.getUserPrincipal() == null) {
            name = "anonymous";
        } else if (!ctx.getUserPrincipal().getName().equals(jwt.getName())) {
            throw new InternalServerErrorException("Principal and JsonWebToken names do not match");
        } else {
            name = ctx.getUserPrincipal().getName();
        }
        return String.format("hello %s,"
        		+ " isHttps: %s,"
        		+ " authScheme: %s,"
        		+ " hasJWT: %s",
        		name, ctx.isSecure(), ctx.getAuthenticationScheme(), hasJwt());
    }

	private boolean hasJwt() {
		return jwt.getClaimNames() != null;
	}

    @GET
    @Path("test_JWT")
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public JsonWebToken getTokenFromCookie(@CookieParam("jwt") String token) throws ParseException{
    //    return token;

        return parser.verify(token,"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxS0FJDDeoPOoLAdyMxdh"+
        "wUhmfde/CEva2LgtONi9SJq5GmERQCVUW8Xd6AwtiwJ5mcf43fgHyhLRX2RtyPtA"+
        "K2WX2kqqABud+KVFs1HOc85At6G9NuIm+Nqrz+LGVOwsRb5Spc/Os4FzKYSrKut8"+
        "np67eRu7MeEYa/JX1RRYcZ4eAQb0oDJaynwNGFlukfjgf/t6O3dHU+w8FXpKooyL"+
        "9NirjYxlYIZNjdQe9l0s+OtEF9Von/pQ52FmF4qYSZ2iIYVlWDEvqnbNXiH29db2"+
        "iOBKM7q8HMUYimQvY87jTZtEOQ16TIjJ+9OLsWpGkWMCVstw846XbYpMTqntbj3r"+
        "5QIDAQAB");
    }
}