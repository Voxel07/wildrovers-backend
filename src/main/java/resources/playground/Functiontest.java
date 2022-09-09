package resources.playground;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;

//Logging
import java.util.logging.Logger;

import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import orm.Secrets.SecretOrm;

@Path("/tests")

@ApplicationScoped
public class Functiontest {
        private static final Logger log = Logger.getLogger(Functiontest.class.getName());

        @Inject
        SecretOrm secretOrm;

        @GET
        @Path("/uuid")
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.APPLICATION_JSON)
        public String testUUID(){
            log.info("Functiontes/testUUID");
            return secretOrm.generateVerificationId();
        }

        @GET
        @Path("/response")
        @Produces(MediaType.APPLICATION_JSON)
        public Response testResponse()
        {
            JsonObject object = Json.createObjectBuilder()
            .add("JWT", 123)
            .add("USER", Json.createObjectBuilder()
                .add("User", "Camo")
                .add("Rolle", "Admin").build()
                )
            .add("Auth", "ja")
            .build();



            return Response.status(418).entity(object).build();
        }
}

