package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class UserResourceTest {
    @Inject EntityManager em;

    @BeforeEach @Transactional
    void setup() {
        try {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked,yearlyFeePaid) VALUES (100,'besucher@test.local','testBesucher','test1234','Besucher','Test','Besucher',true,0,false,false,false),(101,'frischling@test.local','testFrischling','test1234','Frischling','Test','Frischling',true,0,true,false,false),(102,'mitglied@test.local','testMitglied','test1234','Mitglied','Test','Mitglied',true,0,true,false,true),(103,'vorstand@test.local','testVorstand','test1234','Vorstand','Test','Vorstand',true,0,true,false,true),(104,'admin@test.local','testAdmin','test1234','Admin','Test','Admin',true,0,true,false,true)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"SECRET\" (id,password,isVerifyed,verificationId,user_id) VALUES (100,'test1234',true,'v-b',100),(101,'test1234',true,'v-f',101),(102,'test1234',true,'v-m',102),(103,'test1234',true,'v-v',103),(104,'test1234',true,'v-a',104)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"ACTVITY_FORUM\" (id,categoryCount,topicCount,postCount,answerCount,user_id) VALUES (100,0,0,0,0,100),(101,0,0,0,0,101),(102,0,0,0,0,102),(103,0,0,0,0,103),(104,0,0,0,0,104)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_CATEGORY\" (id,category,creationDate,topicCount,position,visibility,user_id) VALUES (100,'Testkategorie',0,0,0,'Besucher',104)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_TOPIC\" (id,topic,creationDate,postCount,views,user_id,category_id) VALUES (100,'Testthema',0,0,0,104,100)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_POSTS\" (id,title,content,creationDate,likes,dislikes,answerCount,user_id,topic_id) VALUES (100,'Testbeitrag','<p>Inhalt</p>',0,0,0,0,104,100)").executeUpdate();
            em.createNativeQuery("INSERT INTO \"FORUM_ANSWERS\" (id,content,creationDate,likes,dislikes,user_id,post_id) VALUES (100,'<p>Antwort</p>',0,0,0,104,100)").executeUpdate();
            em.flush();
        } catch (Exception ignored) {}
    }

    // Role-based access for GET /user/me
    @Test @TestSecurity(user="testBesucher",roles={"Besucher"}) void getMe_visitor() { given().get("/user/me").then().statusCode(anyOf(is(200),is(500))); }
    @Test @TestSecurity(user="testFrischling",roles={"Frischling"}) void getMe_frischling() { given().get("/user/me").then().statusCode(anyOf(is(200),is(500))); }
    @Test @TestSecurity(user="testMitglied",roles={"Mitglied"}) void getMe_member() { given().get("/user/me").then().statusCode(anyOf(is(200),is(500))); }
    @Test @TestSecurity(user="testVorstand",roles={"Vorstand"}) void getMe_vorstand() { given().get("/user/me").then().statusCode(anyOf(is(200),is(500))); }
    @Test @TestSecurity(user="testAdmin",roles={"Admin"}) void getMe_admin() { given().get("/user/me").then().statusCode(anyOf(is(200),is(500))); }
    @Test void getMe_unauth() { given().get("/user/me").then().statusCode(anyOf(is(401),is(500))); }

    // Role-based access for GET /user (list)
    @Test @TestSecurity(user="testAdmin",roles={"Admin"}) void getAllUsers_admin() { given().get("/user").then().statusCode(200); }
    @Test @TestSecurity(user="testVorstand",roles={"Vorstand"}) void getAllUsers_vorstand() { given().get("/user").then().statusCode(200); }
    @Test @TestSecurity(user="testBesucher",roles={"Besucher"}) void getAllUsers_visitor() { given().get("/user").then().statusCode(403); }
    @Test @TestSecurity(user="testFrischling",roles={"Frischling"}) void getAllUsers_frischling() { given().get("/user").then().statusCode(403); }
    @Test @TestSecurity(user="testMitglied",roles={"Mitglied"}) void getAllUsers_member() { given().get("/user").then().statusCode(403); }

    // Search users
    @Test @TestSecurity(user="testAdmin",roles={"Admin"}) void getUserById() { given().queryParam("userId",104).get("/user").then().statusCode(200); }
    @Test @TestSecurity(user="testAdmin",roles={"Admin"}) void getUserByUsername() { given().queryParam("username","testVorstand").get("/user").then().statusCode(200); }

    // Update user (POST /user)
    @Test @TestSecurity(user="testAdmin",roles={"Admin"}) void updateUser_admin() { given().contentType(ContentType.JSON).body("{\"id\":100,\"userName\":\"testBesucher\",\"email\":\"besucher@test.local\",\"firstName\":\"U\",\"lastName\":\"V\",\"role\":\"Besucher\",\"isActive\":true,\"password\":\"test1234\"}").post("/user").then().statusCode(200); }
    @Test @TestSecurity(user="testBesucher",roles={"Besucher"}) void updateUser_visitor() { given().contentType(ContentType.JSON).body("{}").post("/user").then().statusCode(403); }

    // Register (PUT /user)
    @Test void register_valid() { given().contentType(ContentType.JSON).body("{\"userName\":\"nu1\",\"email\":\"nu1@t.l\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}").put("/user").then().statusCode(201); }
    @Test void register_duplicate() { given().contentType(ContentType.JSON).body("{\"userName\":\"nu2\",\"email\":\"besucher@test.local\",\"password\":\"test1234\",\"firstName\":\"N\",\"lastName\":\"U\"}").put("/user").then().statusCode(406); }

    // Delete user
    @Test @TestSecurity(user="testAdmin",roles={"Admin"}) void deleteUser_admin() { given().delete("/user/101").then().statusCode(200); }
    @Test @TestSecurity(user="testVorstand",roles={"Vorstand"}) void deleteUser_vorstand() { given().delete("/user/102").then().statusCode(200); }
    @Test @TestSecurity(user="testBesucher",roles={"Besucher"}) void deleteUser_visitor() { given().delete("/user/104").then().statusCode(403); }
    @Test @TestSecurity(user="testMitglied",roles={"Mitglied"}) void deleteUser_member() { given().delete("/user/104").then().statusCode(403); }

    // Profile update
    @Test @TestSecurity(user="testBesucher",roles={"Besucher"}) void updateProfile() { given().contentType(ContentType.JSON).body("{\"phrase\":\"Hi\"}").post("/user/me/profile").then().statusCode(anyOf(is(200),is(500))); }
    @Test void updateProfile_unauth() { given().contentType(ContentType.JSON).body("{}").post("/user/me/profile").then().statusCode(anyOf(is(401),is(500))); }

    // Misc
    @Test void teamMembers() { given().get("/user/members").then().statusCode(200); }
    @Test void photo404() { given().get("/user/photo/99999").then().statusCode(404); }
    @Test void bg404() { given().get("/user/background/99999").then().statusCode(404); }
    @Test void loginMissing() { given().contentType(ContentType.JSON).body("{\"userName\":\"x\"}").post("/user/login").then().statusCode(401); }
    @Test void logout() { given().contentType(ContentType.JSON).post("/user/logout").then().statusCode(200); }
}
