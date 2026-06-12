package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

//HTTP Requests
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

//Logging
import java.util.logging.Logger;

import jakarta.annotation.security.RolesAllowed;
import model.Forum.ForumCategory;
import orm.Forum.ForumCategoryOrm;
import model.Users.Roles;



@Path("/forum/category")
@ApplicationScoped
public class ForumCategoryResource {
    private static final Logger log = Logger.getLogger(ForumCategoryResource.class.getName());

    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumCategory> getCategorys(@QueryParam("categoryId") Long categoryId,
            @QueryParam("category") String category) {
        log.info("ForumResource/getCategorys");
        List<ForumCategory> categories;
        if (categoryId != null) {
            log.info("ForumResource/getCategorys/id");
            categories = forumCategoryOrm.getCategoriesById(categoryId);
        } else if (category != null) {
            log.info("ForumResource/getCategorys/name");
            categories = forumCategoryOrm.getCategoriesByName(category);
        } else {
            log.info("ForumResource/getCategorys/all");
            categories = forumCategoryOrm.getAllCategories();
        }

        String userRole = Roles.VSISITOR;
        model.User user = userPrincipalResolver.resolveUser();
        if (user != null) {
            userRole = user.getRole();
        }
        final String finalRole = userRole;
        List<ForumCategory> mutableCategories = new java.util.ArrayList<>(categories);
        mutableCategories.removeIf(cat -> {
            String vis = cat.getVisibility();
            if (vis == null || vis.isBlank()) {
                vis = Roles.VSISITOR;
            }
            return !Roles.hasRequiredRole(finalRole, vis);
        });
        return mutableCategories;
    }

    @PUT
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addCategory(@Valid ForumCategory forumCategory) {
        log.info("ForumResource/addCategory");
        model.User user = userPrincipalResolver.resolveUser();

        if (user == null || forumCategory == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        if (Roles.VSISITOR.equals(user.getRole()) && !user.getCanCreateCategory()) {
            return Response.status(403).entity("Du hast keine Berechtigung, Kategorien zu verwalten.").build();
        }
        return forumCategoryOrm.addCategory(forumCategory, user.getId());
    }

    @POST
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCategory(ForumCategory forumCategory) {
        log.info("ForumResource/updateCategory");
        model.User user = userPrincipalResolver.resolveUser();

        if (user == null || forumCategory == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        if (Roles.VSISITOR.equals(user.getRole()) && !user.getCanCreateCategory()) {
            return Response.status(403).entity("Du hast keine Berechtigung, Kategorien zu verwalten.").build();
        }
        String result = forumCategoryOrm.updateCategory(forumCategory, user.getId());
        return Response.ok(result).build();
    }

    @DELETE
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteCategory(ForumCategory forumCategory) {
        log.info("ForumResource/deleteCategory");
        model.User user = userPrincipalResolver.resolveUser();

        if (user == null || forumCategory == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        if (Roles.VSISITOR.equals(user.getRole()) && !user.getCanCreateCategory()) {
            return Response.status(403).entity("Du hast keine Berechtigung, Kategorien zu verwalten.").build();
        }
        return forumCategoryOrm.deleteCategory(forumCategory, user.getId());
    }
}
