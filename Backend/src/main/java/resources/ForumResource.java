package resources;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.swing.text.AttributeSet.ColorAttribute;
import javax.ws.rs.InternalServerErrorException;
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

import model.Forum.ForumCategory;
import model.Forum.ForumPost;

//Logging
import java.util.logging.Logger;

@Path("/forum")
// @RequestScoped
@ApplicationScoped
public class ForumResource {
    private static final Logger log = Logger.getLogger(ForumResource.class.getName());
    @Inject
    ForumOrm forumOrm;

    @GET
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumCategory> getCategorys(@QueryParam("categoryId") Long categorId,@QueryParam("category") String category)
    {
         log.info("ForumResource/getCategorys");
        if(categorId != null){
             log.info("ForumResource/getCategorys/id");
            return forumOrm.getCategoriesById(categorId);
        }
        else if(category != null){
             log.info("ForumResource/getCategorys/name");
            return forumOrm.getCategoriesByName(category);
        }
        else{
             log.info("ForumResource/getCategorys/all");
            return forumOrm.getAllCategories();
        }
    }

    @PUT
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addCategory(ForumCategory fc, @QueryParam("creator") Long categorId){
         log.info("ForumResource/addCategory");
        //Check permissions
        return forumOrm.addCategory(fc,categorId);
    }

    @POST
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateCategory(ForumCategory fc){
        //Check permissions
        return forumOrm.updateCategory(fc);
    }

    @DELETE
    @Path("category")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteCategory(ForumCategory fc){
        //Check permissions
        return forumOrm.removeCategory(fc);

    }
}
