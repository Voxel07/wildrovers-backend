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

import jakarta.annotation.security.RolesAllowed;
import model.Forum.ForumTopic;
import orm.Forum.ForumTopicOrm;

@Path("/forum/topic")
@ApplicationScoped
public class ForumTopicResource {
    private static final Logger log = Logger.getLogger(ForumTopicResource.class.getName());

    @Inject
    ForumTopicOrm forumTopicOrm;

    @Inject
    helper.UserPrincipalResolver userPrincipalResolver;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ForumTopic> getTopics(  @QueryParam("topicId") Long topicId,
                                        @QueryParam("topic") String topic,
                                        @QueryParam("user")Long userId,
                                        @QueryParam("category")Long categoryId)
    {
         log.info("ForumResource/getTopics");
         List<ForumTopic> topics;
         if(topicId != null){
              log.info("ForumResource/getTopics/id");
             topics = forumTopicOrm.getTopicById(topicId);
         }
         else if(topic != null){
              log.info("ForumResource/getTopics/name");
             topics = forumTopicOrm.getTopicsByTopic(topic);
         }
         else if(userId != null){
             log.info("ForumResource/getTopics/user");
             topics = forumTopicOrm.getTopicsByUser(userId);
         }
         else if(categoryId != null){
             log.info("ForumResource/getTopics/category");
             topics = forumTopicOrm.getTopicsByCategory(categoryId);
         }
         else{
              log.info("ForumResource/getTopics/all");
             topics = forumTopicOrm.getAllTopics();
         }

         String userRole = Roles.VSISITOR;
         model.User user = userPrincipalResolver.resolveUser();
         if (user != null) {
             userRole = user.getRole();
         }
         final String finalRole = userRole;
         List<ForumTopic> mutableTopics = new java.util.ArrayList<>(topics);
         mutableTopics.removeIf(t -> {
             model.Forum.ForumCategory cat = t.getCategory();
             if (cat == null) return false;
             String vis = cat.getVisibility();
             if (vis == null || vis.isBlank()) {
                 vis = Roles.VSISITOR;
             }
             return !Roles.hasRequiredRole(finalRole, vis);
         });
         return mutableTopics;
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
        return forumTopicOrm.addTopic(forumTopic, categoryId, user.getId());
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
        String result = forumTopicOrm.updateTopic(ft, user.getId());
        return Response.ok(result).build();
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
        String result = forumTopicOrm.deleteTopic(ft, user.getId());
        return Response.ok(result).build();
    }
}
