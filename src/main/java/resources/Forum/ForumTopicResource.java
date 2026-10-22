package resources.Forum;

//Datentypen
import java.util.List;

//Quarkus zeug
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
//HTTP Requests
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import model.Users.Roles;


//Logging
import java.util.logging.Logger;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import model.Forum.ForumTopic;
import orm.Forum.ForumCategoryOrm;
import orm.Forum.ForumPage;
import orm.Forum.ForumTopicOrm;
import tools.AuditLogger;

@Path("/forum/topic")
@ApplicationScoped
public class ForumTopicResource {
    private static final Logger log = Logger.getLogger(ForumTopicResource.class.getName());

    @Inject
    ForumTopicOrm forumTopicOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @Inject
    ForumCategoryOrm forumCategoryOrm;

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getTopics(  @QueryParam("topicId") Long topicId,
                                        @QueryParam("topic") String topic,
                                        @QueryParam("user")Long userId,
                                        @QueryParam("category")Long categoryId,
                                        @QueryParam("page") Integer page,
                                        @QueryParam("size") Integer size)
    {
         log.info("ForumResource/getTopics");
         model.User user = userPrincipalResolver.resolveUser();
         String userRole = user != null ? user.getRole() : Roles.VSISITOR;
         ForumPage<ForumTopic> result = forumTopicOrm.findTopics(topicId, topic, userId, categoryId,
                 Roles.visibleTo(userRole), page, size);
         List<ForumTopic> topics = new java.util.ArrayList<>(result.items());
         forumTopicOrm.attachSummaries(topics, user != null ? user.getId() : null);
         return Response.ok(topics).header(ForumPage.TOTAL_HEADER, result.total()).build();
    }

    @PUT
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addTopic(ForumTopic forumTopic, @QueryParam("category") Long categoryId){
        log.info("ForumResource/addTopic");
        model.User user = userPrincipalResolver.resolveUser();

        if (user == null || forumTopic == null || forumTopic.getTopic() == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        if (Roles.VSISITOR.equals(user.getRole()) && !user.getCanCreateCategory()) {
            return Response.status(403).entity("Besucher dürfen keine Themen erstellen/verwalten.").build();
        }
        AuditLogger.crud(log, user.getUserName(), user.getId(), "CREATE", "Topic",
                forumTopic.getTopic());
        return invalidateCategoriesOnSuccess(forumTopicOrm.addTopic(forumTopic, categoryId, user.getId()));
    }

    @POST
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateTopic(ForumTopic ft){
        log.info("ForumResource/updateTopic");
        model.User user = userPrincipalResolver.resolveUser();

        if (user == null || ft == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        if (Roles.VSISITOR.equals(user.getRole()) && !user.getCanCreateCategory()) {
            return Response.status(403).entity("Besucher dürfen keine Themen erstellen/verwalten.").build();
        }
        AuditLogger.crud(log, user.getUserName(), user.getId(), "UPDATE", "Topic", ft.getId());
        return forumTopicOrm.updateTopic(ft, user.getId());
    }

    @DELETE
    @RolesAllowed({ Roles.VSISITOR, Roles.FRESHMAN, Roles.MEMBER, Roles.ALDERMEN, Roles.ADMIN })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteTopic(ForumTopic ft){
        log.info("ForumResource/deleteTopic");
        model.User user = userPrincipalResolver.resolveUser();

        if (user == null || ft == null) {
            return Response.status(401).entity("Fehlender oder falscher Parameter").build();
        }
        if (Roles.VSISITOR.equals(user.getRole()) && !user.getCanCreateCategory()) {
            return Response.status(403).entity("Besucher dürfen keine Themen erstellen/verwalten.").build();
        }
        AuditLogger.crud(log, user.getUserName(), user.getId(), "DELETE", "Topic", ft.getId());
        return invalidateCategoriesOnSuccess(forumTopicOrm.deleteTopic(ft, user.getId()));
    }

    /**
     * Cached categories carry the topic count. Invalidated after the ORM transaction has
     * committed, so a concurrent read cannot re-cache the old count.
     */
    private Response invalidateCategoriesOnSuccess(Response response) {
        if (response.getStatus() / 100 == 2) {
            forumCategoryOrm.invalidateCategoryCaches();
        }
        return response;
    }
}
