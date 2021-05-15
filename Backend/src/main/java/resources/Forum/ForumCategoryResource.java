package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
//HTTP Requests
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;


//Logging
import java.util.logging.Logger;

//Eigene Imports
import model.Forum.ForumCategory;
import orm.Forum.ForumCategoryOrm;

//Sicherheits Zeug
import javax.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.security.Principal;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

@Path("/forum/category")
// @RequestScoped
@ApplicationScoped
public class ForumCategoryResource {
    private static final Logger log = Logger.getLogger(ForumCategoryResource.class.getName());
    
    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumCategory> getCategorys(@QueryParam("categoryId") Long categoryId,@QueryParam("category") String category)
    {
         log.info("ForumResource/getCategorys");
        if(categoryId != null){
             log.info("ForumResource/getCategorys/id");
            return forumCategoryOrm.getCategoriesById(categoryId);
        }
        else if(category != null){
             log.info("ForumResource/getCategorys/name");
            return forumCategoryOrm.getCategoriesByName(category);
        }
        else{
             log.info("ForumResource/getCategorys/all");
            return forumCategoryOrm.getAllCategories();
        }
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String addCategory(ForumCategory forumCategory, @QueryParam("creator") Long userId){
         log.info("ForumResource/addCategory");
        /*
        ToDo
        -   Check permissions
        */
        if(userId == null) return "Fehleder oder falscher Parameter";
        return forumCategoryOrm.addCategory(forumCategory,userId);
    }
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String updateCategory(ForumCategory forumCategory){
        log.info("ForumResource/updateCategory");
        /*
        @ToDo
        -   Check permissions
        */
        return forumCategoryOrm.updateCategory(forumCategory);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String deleteCategory(ForumCategory forumCategory){
        log.info("ForumResource/deleteCategory");
        /*
        @ToDo
        -   Check permissions
        */
        return forumCategoryOrm.removeCategory(forumCategory);
    }
}
