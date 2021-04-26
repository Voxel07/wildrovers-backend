package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.swing.text.AttributeSet.ColorAttribute;
import javax.ws.rs.InternalServerErrorException;
//Logging zeug
import org.jboss.logging.Logger;
//HTTP Requests
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

//Eigene Imports
import model.User;
import orm.ForumOrm;
import orm.UserOrm;
import javax.ws.rs.QueryParam;

//Emailzeug
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.reactive.ReactiveMailer;

//Sicherheits Zeug
import javax.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.jwt.JsonWebToken;
import java.security.Principal;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import model.Forum.ForumPost;

@Path("/user")
// @RequestScoped
@ApplicationScoped
public class ForumResource {
    
    @Inject
    ForumOrm forumOrm;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumPost> getKategorys(){
        return forumOrm.getPosts();
    }
}
