package resources.playground;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.inject.Inject;
//Logging
import java.util.logging.Logger;

import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import orm.Secrets.SecretOrm;

@Path("/tests/uuid")

@ApplicationScoped
public class Functiontest {
        private static final Logger log = Logger.getLogger(Functiontest.class.getName());

        @Inject
        SecretOrm secretOrm;


        @GET
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.APPLICATION_JSON)
        public String testUUID(){
            return secretOrm.generateVerificationId();
        }

}

