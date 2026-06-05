package resources.playground;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import javax.print.attribute.standard.Media;

//Logging
import java.util.logging.Logger;
import java.util.HashMap;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import orm.Secrets.SecretOrm;
import resources.JWT;

import jakarta.ws.rs.core.NewCookie;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import io.vertx.core.http.HttpServerRequest;

@Path("/tests")

@ApplicationScoped
public class Functiontest {

        private static final Logger log = Logger.getLogger(Functiontest.class.getName());

        private static HashMap<String,Long> blockList = new HashMap<String,Long>();

        private static Long count = 0L;

        private static String IP;

        private static String getIp() {
            return IP;
        }

        private static void setIp(String newIp){
            if (!newIp.equals(getIp()))
            {
                IP=newIp;
                rstCnt();
            }
            incCount();
        }

        private static void updateList(String newIp)
        {
            Long tries  = blockList.get(newIp);

            if(tries == null){
                blockList.put(newIp, 0L);
            }
            else{
                blockList.put(newIp, blockList.get(newIp)+1);
            }
        }
        private static Long getValFromList(String key)
        {
            Long val = blockList.get(key);
            if (val == null) return 0L;
            return val;
        }

        private static void rstCnt(){
            count=0L;
        }
        private static void incCount(){
            count += 1;
        }

        private static Long getCount(){
            return count;
        }

        @Inject
        SecretOrm secretOrm;

        @Inject
        jakarta.persistence.EntityManager em;

        @Inject
        orm.EventOrm eventOrm;

        @Inject
        orm.Forum.ForumPostOrm forumPostOrm;

        @Context
        HttpServerRequest request;

        @Context
        UriInfo info;

        @GET
        @Path("/run-delete-test")
        @Produces(MediaType.APPLICATION_JSON)
        public Response runDeleteTest() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            try {
                // Find a user
                model.User user = em.createQuery("SELECT u FROM User u", model.User.class).setMaxResults(1).getSingleResult();
                map.put("userFound", user.getUserName());

                // 1. Create forum post
                model.Forum.ForumPost post = new model.Forum.ForumPost();
                post.setTitle("Test Event Post " + System.currentTimeMillis());
                post.setContent("This is a test event description.");
                Response postResponse = forumPostOrm.addPost(post, 2L, user.getId());
                if (postResponse.getStatus() != 201) {
                    throw new RuntimeException("Failed to create post: " + postResponse.getEntity());
                }
                Long postId = (Long) postResponse.getEntity();
                map.put("createdPostId", postId);

                // 2. Create Event
                model.Event event = new model.Event();
                event.setTitle("Test Event " + System.currentTimeMillis());
                event.setLocation("Test Location");
                event.setEventDate(java.time.LocalDateTime.now().plusDays(1));
                event.setForumPostUrl("http://localhost:5173/Forum/Post/" + postId);
                model.Event createdEvent = eventOrm.addEvent(event, user.getId());
                map.put("createdEventId", createdEvent.getId());

                // 3. Verify post exists before deletion
                model.Forum.ForumPost postBefore = em.find(model.Forum.ForumPost.class, postId);
                map.put("postExistsBeforeDelete", postBefore != null);
                if (postBefore != null) {
                    map.put("titleBefore", postBefore.getTitle());
                    map.put("contentBefore", postBefore.getContent());
                    map.put("creatorBefore", postBefore.getCreator());
                    map.put("likesBefore", postBefore.getLikes());
                    map.put("dislikesBefore", postBefore.getDislikes());
                    map.put("viewsBefore", postBefore.getViews());
                    map.put("topicIdBefore", postBefore.getTopic() != null ? postBefore.getTopic().getId() : null);
                }

                // 4. Delete the Event
                eventOrm.deleteEvent(createdEvent.getId());
                map.put("eventDeleted", true);

                // 5. Verify post exists after deletion
                model.Forum.ForumPost postAfter = em.find(model.Forum.ForumPost.class, postId);
                map.put("postExistsAfterDelete", postAfter != null);
                if (postAfter != null) {
                    map.put("titleAfter", postAfter.getTitle());
                    map.put("contentAfter", postAfter.getContent());
                    map.put("creatorAfter", postAfter.getCreator());
                    map.put("likesAfter", postAfter.getLikes());
                    map.put("dislikesAfter", postAfter.getDislikes());
                    map.put("viewsAfter", postAfter.getViews());
                    map.put("topicIdAfter", postAfter.getTopic() != null ? postAfter.getTopic().getId() : null);
                }

                return Response.ok(map).build();
            } catch (Throwable e) {
                log.log(java.util.logging.Level.SEVERE, "Delete test failed", e);
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                map.put("error", e.getMessage());
                map.put("stackTrace", sw.toString());
                return Response.ok(map).build();
            }
        }

        @GET
        @Path("/post-detail")
        @Produces(MediaType.APPLICATION_JSON)
        public Response postDetail(@QueryParam("id") Long id) {
            try {
                model.Forum.ForumPost p = em.find(model.Forum.ForumPost.class, id);
                if (p == null) {
                    return Response.status(404).entity("Post not found").build();
                }
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", p.getId());
                map.put("title", p.getTitle());
                map.put("content", p.getContent());
                map.put("creatorName", p.getCreator());
                map.put("creatorObjNull", p.getCreatorObj() == null);
                map.put("editorNull", p.getEditor() == null);
                map.put("topicId", p.getTopic() != null ? p.getTopic().getId() : null);
                map.put("likes", p.getLikes());
                map.put("dislikes", p.getDislikes());
                map.put("views", p.getViews());
                map.put("answerCount", p.getAnswerCount());
                map.put("answersSize", p.getAnswers() != null ? p.getAnswers().size() : 0);
                map.put("picturesSize", p.getPictures() != null ? p.getPictures().size() : 0);
                map.put("pollNull", p.getPoll() == null);
                return Response.ok(map).build();
            } catch (Exception e) {
                log.log(java.util.logging.Level.SEVERE, "postDetail failed", e);
                return Response.status(500).entity(e.getMessage()).build();
            }
        }

        @GET
        @Path("/list-all")
        @Produces(MediaType.APPLICATION_JSON)
        public Response listAll() {
            try {
                java.util.List<model.Event> events = em.createQuery("SELECT e FROM Event e", model.Event.class).getResultList();
                java.util.List<model.Forum.ForumPost> posts = em.createQuery("SELECT p FROM ForumPost p", model.Forum.ForumPost.class).getResultList();
                
                java.util.List<java.util.Map<String, Object>> eventList = new java.util.ArrayList<>();
                for (model.Event e : events) {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("id", e.getId());
                    item.put("title", e.getTitle());
                    item.put("forumPostUrl", e.getForumPostUrl());
                    eventList.add(item);
                }
                
                java.util.List<java.util.Map<String, Object>> postList = new java.util.ArrayList<>();
                for (model.Forum.ForumPost p : posts) {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("id", p.getId());
                    item.put("title", p.getTitle());
                    item.put("topicId", p.getTopic() != null ? p.getTopic().getId() : null);
                    postList.add(item);
                }
                
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("events", eventList);
                map.put("posts", postList);
                return Response.ok(map).build();
            } catch (Exception e) {
                log.log(java.util.logging.Level.SEVERE, "listAll failed", e);
                return Response.status(500).entity(e.getMessage()).build();
            }
        }

        @GET
        @Path("/call-posts")
        @Produces(MediaType.APPLICATION_JSON)
        public Response callPosts(@QueryParam("id") Long id) {
            log.info("HOT-RELOAD TEST: callPosts endpoint called with id = " + id);
            try {
                java.util.List<model.Forum.ForumPost> list = forumPostOrm.getPostsById(id);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                
                // Register mixins to ensure Jackson ignores lazy properties on Hibernate enhanced classes/proxies
                mapper.addMixIn(model.Forum.ForumPost.class, ForumPostMixIn.class);
                mapper.addMixIn(model.Forum.ForumAnswer.class, ForumAnswerMixIn.class);
                mapper.addMixIn(model.Forum.ForumTopic.class, ForumTopicMixIn.class);
                mapper.addMixIn(model.Forum.ForumCategory.class, ForumCategoryMixIn.class);
                mapper.addMixIn(model.User.class, UserMixIn.class);
                mapper.addMixIn(model.Event.class, EventMixIn.class);
                mapper.addMixIn(model.EventAttendance.class, EventAttendanceMixIn.class);

                String json = mapper.writeValueAsString(list);
                return Response.ok(json).build();
            } catch (Throwable e) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                java.util.Map<String, String> map = new java.util.HashMap<>();
                map.put("error", e.getMessage());
                map.put("stackTrace", sw.toString());
                return Response.ok(map).build();
            }
        }

        @GET
        @Path("/serialize-post")
        @Produces(MediaType.APPLICATION_JSON)
        public Response serializePost(@QueryParam("id") Long id) {
            try {
                model.Forum.ForumPost p = em.find(model.Forum.ForumPost.class, id);
                jakarta.json.bind.Jsonb jsonb = jakarta.json.bind.JsonbBuilder.create();
                String json = jsonb.toJson(p);
                return Response.ok(json).build();
            } catch (Throwable e) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                java.util.Map<String, String> map = new java.util.HashMap<>();
                map.put("error", e.getMessage());
                map.put("stackTrace", sw.toString());
                return Response.ok(map).build();
            }
        }

        @GET
        @Path("/git-diff")
        @Produces(MediaType.TEXT_PLAIN)
        public Response gitDiff() {
            try {
                Process process = new ProcessBuilder("git", "diff")
                    .directory(new java.io.File("d:\\Code\\wildrovers-backend"))
                    .redirectErrorStream(true)
                    .start();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                process.waitFor();
                return Response.ok(sb.toString()).build();
            } catch (Exception e) {
                log.log(java.util.logging.Level.SEVERE, "gitDiff failed", e);
                return Response.status(500).entity(e.getMessage()).build();
            }
        }

        @GET
        @Path("/db-debug")
        @Produces(MediaType.APPLICATION_JSON)
        public Response dbDebug() {
            try {
                Object eventSchema = em.createNativeQuery("SHOW CREATE TABLE EVENT").getSingleResult();
                Object postSchema = em.createNativeQuery("SHOW CREATE TABLE FORUM_POSTS").getSingleResult();
                Object attendanceSchema = em.createNativeQuery("SHOW CREATE TABLE EVENT_ATTENDANCES").getSingleResult();
                
                java.util.List<?> allFKs = em.createNativeQuery(
                    "SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                    "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                    "WHERE REFERENCED_TABLE_SCHEMA = 'wildrovers'"
                ).getResultList();

                java.util.List<?> eventRows = em.createNativeQuery("SELECT * FROM EVENT").getResultList();
                java.util.List<?> postRows = em.createNativeQuery("SELECT * FROM FORUM_POSTS").getResultList();
                
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("eventSchema", eventSchema);
                map.put("postSchema", postSchema);
                map.put("attendanceSchema", attendanceSchema);
                map.put("foreignKeys", allFKs);
                map.put("eventCount", eventRows.size());
                map.put("postCount", postRows.size());
                return Response.ok(map).build();
            } catch (Exception e) {
                log.log(java.util.logging.Level.SEVERE, "DB debug failed", e);
                return Response.status(500).entity(e.getMessage()).build();
            }
        }

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

            NewCookie klaus = new NewCookie.Builder("rolf")
                    .value("4567")
                    .path("/")
                    .domain("localhost")
                    .comment("test")
                    .maxAge(3600)
                    .secure(true)
                    .httpOnly(true)
                    .build();

            return Response.status(200).entity(object)
            .cookie(klaus)
            .build();
        }

        @GET
        @Path("/remove")
        @Produces(MediaType.APPLICATION_JSON)
        public Response removeCookie()
        {
            NewCookie klaus = new NewCookie.Builder("rolf")
                    .value("deleted")
                    .path("/")
                    .domain("localhost")
                    .comment("test")
                    .maxAge(0)
                    .secure(true)
                    .httpOnly(true)
                    .build();

            return Response.status(200).entity("object").header("Set-Cookie", klaus).build();
        }

        @GET
        @Path("/count")
        @Produces(MediaType.TEXT_PLAIN)
        public Response statisch()
        {
            // incCount();

            // JsonObject object = Json.createObjectBuilder()
            // .add("count", getCount())
            // .add("HttpServerRequest", Json.createObjectBuilder()
            //     .add("host", request.host())
            //     .add("cookieCount", request.cookieCount())
            //     .add("remoteAddress", request.remoteAddress().toString())
            //     .add("localAddress", request.localAddress().toString())
            //     .add("Rolle", "Admin")
            //     .add("HttpServerRequest.response", Json.createObjectBuilder()
            //         .add("getStatusCode", request.response().getStatusCode())
            //         .add("getStatusMessage", request.response().getStatusMessage())
            //         .add("getClass", request.response().getClass().toString())
            //         .build())
            //     .add("HttpServerRequest.connection", Json.createObjectBuilder()
            //     .add("getWindowSize", request.connection().getWindowSize())
            //     .add("isSsl", request.connection().isSsl())
            //     .add("getClass", request.connection().sslSession().getSessionContext().getSessionCacheSize())
            //     .build())
            //     .build())
            // .add("Auth", "ja")
            // .build();
            // log.info("Info: " + info.getQueryParameters() + "|" + info.getBaseUri() + "|" + info.getPath() + "|" + info.getAbsolutePath());
            // return Response.status(200).entity(object).build();

            setIp(request.remoteAddress().toString());
            Response res;
            log.info(blockList.toString());

            Long tries = getValFromList(request.remoteAddress().toString());

            if(tries > 10){
            updateList(request.remoteAddress().toString());

                res = Response.status(401).entity("why"+tries).build();
            }
            else{
            updateList(request.remoteAddress().toString());

                res = Response.status(200).entity("ok"+tries).build();
            }

            return res;
        }

        // Jackson MixIns to prevent LazyInitializationException on enhanced proxies/subclasses
        public interface ForumPostMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.Forum.ForumTopic getTopic();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getEditor();
            @com.fasterxml.jackson.annotation.JsonIgnore
            java.util.List<model.Forum.ForumAnswer> getAnswers();
            @com.fasterxml.jackson.annotation.JsonIgnore
            java.util.List<model.Forum.ForumPicture> getPictures();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getCreatorObj();
        }

        public interface ForumAnswerMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.Forum.ForumPost getPost();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getEditor();
            @com.fasterxml.jackson.annotation.JsonIgnore
            java.util.List<model.Forum.ForumPicture> getPictures();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getCreatorObj();
        }

        public interface ForumTopicMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.Forum.ForumCategory getCategory();
            @com.fasterxml.jackson.annotation.JsonIgnore
            java.util.List<model.Forum.ForumPost> getPosts();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getCreatorObj();
        }

        public interface ForumCategoryMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            java.util.List<model.Forum.ForumTopic> getTopics();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getCreatorObj();
        }

        public interface UserMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getMentor();
            @com.fasterxml.jackson.annotation.JsonIgnore
            java.util.List<model.User> getMentees();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.Users.Secret getSecret();
        }

        public interface EventMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getCreator();
        }

        public interface EventAttendanceMixIn {
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.User getUser();
            @com.fasterxml.jackson.annotation.JsonIgnore
            model.Event getEvent();
        }
}


