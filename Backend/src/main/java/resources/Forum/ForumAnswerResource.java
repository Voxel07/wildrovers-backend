package resources.Forum;
//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;

//Logging
import java.util.logging.Logger;
public class ForumAnswerResource {
    private static final Logger log = Logger.getLogger(ForumAnswerResource.class.getName());
}
