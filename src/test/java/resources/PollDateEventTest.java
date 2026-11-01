package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.Forum.ForumPost;
import model.Forum.Polls.PollOptions;
import model.Forum.Polls.Polls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Date options of polls and events created from a poll option. */
@QuarkusTest
// Same profile as EventResourceTest: no Google Calendar calls, events topic 250, no restart.
@TestProfile(EventResourceTest.Profile.class)
class PollDateEventTest {

    private static final long POST_ID = 600;

    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        Number existing = (Number) em.createNativeQuery("SELECT COUNT(*) FROM \"USER\" WHERE id = 600").getSingleResult();
        if (existing.intValue() == 0) {
            insertUser(600, "pollOwner", "Mitglied");
            insertUser(601, "pollOther", "Mitglied");
            insertUser(602, "pollVisitor", "Besucher");
            insertUser(603, "pollMod", "Vorstand");
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (600,'PollDateCat',0,1,60,'Besucher',600)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (600,'PollDateTopic',0,1,0,600,600)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (600,'Welches Wochenende?','<p>C</p>',0,0,0,0,600,600)").executeUpdate();
        }
        // Topic for the event discussion posts (app.events.forum-topic-id=250 in the profile).
        Number eventTopic = (Number) em.createNativeQuery("SELECT COUNT(*) FROM \"FORUM_TOPIC\" WHERE id = 250").getSingleResult();
        if (eventTopic.intValue() == 0) {
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (250,'EventCat',0,1,50,'Besucher',NULL)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (250,'Events',0,0,0,NULL,250)").executeUpdate();
        }
    }

    private void insertUser(long id, String name, String role) {
        em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) VALUES ("
                + id + ",'" + name + "@t.l','" + name + "','test1234','Poll','Test','" + role + "',true,0,false,false)").executeUpdate();
        em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES ("
                + id + ",'test1234',true,'v-" + name + "'," + id + ")").executeUpdate();
    }

    /** Poll of the owner's post with two weekend options; returns the option ids. */
    @Transactional
    List<Long> createWeekendPoll() {
        Polls poll = new Polls();
        poll.setQuestion("Welches Wochenende?");
        poll.setPost(em.find(ForumPost.class, POST_ID));
        for (int week = 0; week < 2; week++) {
            PollOptions option = new PollOptions("Wochenende " + week);
            option.setOptionDate(LocalDate.of(2031, 11, 8).plusWeeks(week));
            option.setOptionEndDate(LocalDate.of(2031, 11, 9).plusWeeks(week));
            option.setPoll(poll);
            poll.getOptions().add(option);
        }
        em.persist(poll);
        em.flush();
        return poll.getOptions().stream().map(PollOptions::getId).toList();
    }

    @Transactional
    Long eventIdOf(Long optionId) {
        return em.find(PollOptions.class, optionId).getEventId();
    }

    @Transactional
    long eventCount() {
        return em.createQuery("SELECT COUNT(e) FROM Event e", Long.class).getSingleResult();
    }

    /** Event titles must be unique per run: the discussion posts land in one topic. */
    private static String eventBody(String title) {
        return "{\"title\":\"" + title + " " + System.nanoTime() + "\",\"eventDate\":\"2031-11-08T10:00:00\","
                + "\"eventEndDate\":\"2031-11-09T18:00:00\",\"location\":\"Platz\"}";
    }

    // ── Date options ──

    @Test @TestSecurity(user = "pollOwner", roles = {"Mitglied"})
    void dateOptionsAreStoredAndKeptOnUpdate() {
        String body = "{\"question\":\"Wann?\",\"options\":["
                + "{\"optionText\":\"Sa, 07.11. – So, 08.11.2026\",\"optionDate\":\"2026-11-07\",\"optionEndDate\":\"2026-11-08\",\"eventId\":999},"
                + "{\"optionText\":\"Di, 10.11.2026\",\"optionDate\":\"2026-11-10\"}]}";
        var created = given().contentType(ContentType.JSON).queryParam("post", POST_ID).body(body)
                .post("/forum/poll/create").then().statusCode(201)
                .body("options[0].optionDate", is("2026-11-07"))
                .body("options[0].optionEndDate", is("2026-11-08"))
                // Event links are never taken from the client.
                .body("options[0].eventId", nullValue())
                .body("options[1].optionEndDate", nullValue())
                .extract();
        Number pollId = created.path("id");
        Number first = created.path("options[0].id");
        Number second = created.path("options[1].id");

        String update = "{\"question\":\"Wann genau?\",\"options\":["
                + "{\"id\":" + first + ",\"optionText\":\"Erstes Wochenende\",\"optionDate\":\"2026-11-07\",\"optionEndDate\":\"2026-11-08\"},"
                + "{\"id\":" + second + ",\"optionText\":\"Dienstag\",\"optionDate\":\"2026-11-10\"},"
                + "{\"optionText\":\"Freitext\"}]}";
        given().contentType(ContentType.JSON).queryParam("poll", pollId).body(update)
                .post("/forum/poll/update").then().statusCode(200);

        given().queryParam("post", POST_ID).get("/forum/post").then().statusCode(200)
                .body("[0].polls.find { it.id == " + pollId + " }.options.find { it.id == " + first + " }.optionDate", is("2026-11-07"))
                .body("[0].polls.find { it.id == " + pollId + " }.options.find { it.id == " + first + " }.optionText", is("Erstes Wochenende"))
                .body("[0].polls.find { it.id == " + pollId + " }.options.find { it.optionText == 'Freitext' }.optionDate", nullValue());
    }

    @Test @TestSecurity(user = "pollOther", roles = {"Mitglied"})
    void voteReturnsPollWithOptions() {
        List<Long> optionIds = createWeekendPoll();
        Long pollId = pollIdOf(optionIds.get(0));
        given().queryParam("poll", pollId).queryParam("option", optionIds.get(1))
                .post("/forum/poll/vote").then().statusCode(200)
                .body("options.find { it.id == " + optionIds.get(1) + " }.votes", is(1))
                .body("options.find { it.id == " + optionIds.get(1) + " }.optionDate", is("2031-11-15"));
    }

    @Transactional
    Long pollIdOf(Long optionId) {
        return em.find(PollOptions.class, optionId).getPoll().getId();
    }

    @Test @TestSecurity(user = "pollOwner", roles = {"Mitglied"})
    void endBeforeStartIs400() {
        String body = "{\"question\":\"Wann?\",\"options\":["
                + "{\"optionText\":\"A\",\"optionDate\":\"2026-11-08\",\"optionEndDate\":\"2026-11-07\"},{\"optionText\":\"B\"}]}";
        given().contentType(ContentType.JSON).queryParam("post", POST_ID).body(body)
                .post("/forum/poll/create").then().statusCode(400);
        String endOnly = "{\"question\":\"Wann?\",\"options\":[{\"optionText\":\"A\",\"optionEndDate\":\"2026-11-07\"},{\"optionText\":\"B\"}]}";
        given().contentType(ContentType.JSON).queryParam("post", POST_ID).body(endOnly)
                .post("/forum/poll/create").then().statusCode(400);
    }

    @Test @TestSecurity(user = "pollOwner", roles = {"Mitglied"})
    void tooManyOptionsIs400() {
        List<Map<String, String>> options = new ArrayList<>();
        for (int i = 0; i <= orm.Forum.ForumPollOrm.MAX_OPTIONS; i++) options.add(Map.of("optionText", "O" + i));
        given().contentType(ContentType.JSON).queryParam("post", POST_ID)
                .body(Map.of("question", "Viele?", "options", options))
                .post("/forum/poll/create").then().statusCode(400);
    }

    // ── Event from a poll option ──

    @Test @TestSecurity(user = "pollOwner", roles = {"Mitglied"})
    void ownerCreatesEventOnceAndDeletingItFreesTheOption() {
        Long optionId = createWeekendPoll().get(0);

        Number eventId = given().contentType(ContentType.JSON).queryParam("pollOption", optionId)
                .body(eventBody("Ausflug")).post("/event").then().statusCode(201)
                .extract().path("id");
        assertEquals(eventId.longValue(), eventIdOf(optionId));

        long before = eventCount();
        given().contentType(ContentType.JSON).queryParam("pollOption", optionId)
                .body(eventBody("Nochmal")).post("/event").then().statusCode(409);
        assertEquals(before, eventCount(), "a rejected link must not leave an event behind");

        given().delete("/event/" + eventId).then().statusCode(200);
        assertNull(eventIdOf(optionId));
    }

    @Test @TestSecurity(user = "pollOther", roles = {"Mitglied"})
    void otherMemberIsForbidden() {
        Long optionId = createWeekendPoll().get(0);
        long before = eventCount();
        given().contentType(ContentType.JSON).queryParam("pollOption", optionId)
                .body(eventBody("Fremd")).post("/event").then().statusCode(403);
        assertEquals(before, eventCount());
        assertNull(eventIdOf(optionId));
    }

    @Test @TestSecurity(user = "pollVisitor", roles = {"Besucher"})
    void visitorIsForbidden() {
        Long optionId = createWeekendPoll().get(0);
        given().contentType(ContentType.JSON).queryParam("pollOption", optionId)
                .body(eventBody("Besuch")).post("/event").then().statusCode(403);
        assertNull(eventIdOf(optionId));
    }

    @Test @TestSecurity(user = "pollMod", roles = {"Vorstand"})
    void moderatorMayCreateEventFromOthersPoll() {
        Long optionId = createWeekendPoll().get(1);
        given().contentType(ContentType.JSON).queryParam("pollOption", optionId)
                .body(eventBody("Vorstand")).post("/event").then().statusCode(201);
        assertNotNull(eventIdOf(optionId));
    }

    @Test @TestSecurity(user = "pollOwner", roles = {"Mitglied"})
    void unknownOptionIs404() {
        long before = eventCount();
        given().contentType(ContentType.JSON).queryParam("pollOption", 999999)
                .body(eventBody("Unbekannt")).post("/event").then().statusCode(404);
        assertEquals(before, eventCount());
    }
}
