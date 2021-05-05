import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import model.User;
import model.Address;
import model.Phone;

import java.util.Arrays;
import java.util.List;

//Logging
import java.util.logging.Logger;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class test_user {
    private static final Logger log = Logger.getLogger(test_user.class.getName());
    private static User userA = new User("voxel@web.de", "Camo", "123", "Matthias", "Schneider", "Vorstand", true);
    private static User userB = new User("matze.schneider95@web.de", "Voxel", "123", "Ralf", "Müller", "Mitglied",
            false);
    private static Address addA = new Address("Limburgweg","Nottingen",73274L,25L,"");

    public void AddUser(User u, String answer) {
        given().contentType(MediaType.APPLICATION_JSON).body(u).when().post("/user").then().statusCode(200)
                .body(is(answer));
    }

    public void AddAddress(User u, Address a, String answer) {

    }

    public List<User> GetUsers() {
        Response resp = given().contentType(MediaType.APPLICATION_JSON).when().get("/user");
        resp.then().statusCode(200);
        return Arrays.asList(resp.getBody().as(User[].class));
    }

    public User GetUser(Long id, String username) {
         log.info("ID:" + id + " Username: " + username);
        if (id != 0 && username == "") {
            Response resp = given().queryParam("userId", id).contentType(MediaType.APPLICATION_JSON).when()
                    .get("/user");
            resp.then().statusCode(200);
            return Arrays.asList(resp.getBody().as(User[].class)).get(0);
        } else {
            Response resp = given().queryParam("userName", username).contentType(MediaType.APPLICATION_JSON).when()
                    .get("/user");
            resp.then().statusCode(200);
            return Arrays.asList(resp.getBody().as(User[].class)).get(0);
        }

    }

    public void CheckUserValues(User javaObj, User ausDB) {
        Assertions.assertEquals(javaObj.getEmail(), ausDB.getEmail());
        Assertions.assertEquals(javaObj.getFirstName(), ausDB.getFirstName());
        Assertions.assertEquals(javaObj.getLastName(), ausDB.getLastName());
        Assertions.assertEquals(javaObj.getUserName(), ausDB.getUserName());
        Assertions.assertEquals(javaObj.getActive(), ausDB.getActive());
        Assertions.assertEquals(javaObj.getRole(), ausDB.getRole());
        Assertions.assertEquals(javaObj.getPassword(), ausDB.getPassword());
        if (javaObj.getId() == null) {
             log.info("Setting ID:" + ausDB.getId() + " to userName: " + javaObj.getUserName());
            javaObj.setId(ausDB.getId());
        }
    }

    public void CheckUserAddress(User u, Address a) {
        Assertions.assertEquals(u.getAddress().getAddressSupplements(), a.getAddressSupplements());
        Assertions.assertEquals(u.getAddress().getPostalcode(), a.getPostalcode());
        Assertions.assertEquals(u.getAddress().getState(), a.getState());
        Assertions.assertEquals(u.getAddress().getStreet(), a.getStreet());
    }

    public void CheckUserPhone(User u, List<Phone> p) {
        for (int i = 0; i < p.size(); i++) {
            Assertions.assertEquals(u.getPhones().get(i).getNumber(), p.get(i).getNumber());
            Assertions.assertEquals(u.getPhones().get(i).getType(), p.get(i).getType());
        }
    }

    @Test
    @Order(1)
    void TestAddUser() {
        AddUser(userA, "1");
    }

    @Test
    @Order(2)
    void TestAddUserDublicate() {
        AddUser(userA, "Nutzer bereits bekannt");
    }

    @Test
    @Order(3)
    void TestAddUser2() {
        AddUser(userB, "2");
    }

    @Test
    @Order(4)
    void TestGetUsers() {
        List<User> aktUserInDB = GetUsers();
        Assertions.assertEquals(2, aktUserInDB.size());
        CheckUserValues(userA, aktUserInDB.get(0));
        CheckUserValues(userB, aktUserInDB.get(1));
    }

    @Test
    @Order(5)
    void TestGetUser() {
        CheckUserValues(userA, GetUser(userA.getId(), ""));
        CheckUserValues(userA, GetUser(0L, userA.getUserName()));
    }

    @Test
    @Order(6)
    void TestUpdateUser() {
        userA.setFirstName("newFirstName");
         log.info("looking for user: "+ userA.getUserName() + "email:" +userA.getFirstName());
        given().contentType(MediaType.APPLICATION_JSON).body(userA).when().put("/user").then().statusCode(200)
                .body(is("User erfolgreich aktualisiert"));

        userA.setUserName("newUserName");
         log.info("looking for user: "+ userA.getFirstName() + "email:" +userA.getUserName());
        given().contentType(MediaType.APPLICATION_JSON).body(userA).when().put("/user").then().statusCode(200)
                .body(is("User erfolgreich aktualisiert"));
        
        userA.setEmail("newEmail");
         log.info("looking for user: "+ userA.getUserName() + "email:" +userA.getEmail());
        given().contentType(MediaType.APPLICATION_JSON).body(userA).when().put("/user").then().statusCode(200)
                .body(is("User erfolgreich aktualisiert"));

    }

    @Test
    @Order(7)
    void TestUpdateUser2() {
        userA.setUserName("newUserName2");
        userA.setEmail("newEmail2");
         log.info("looking for user: "+ userA.getUserName() + "email:" +userA.getEmail());
        given().contentType(MediaType.APPLICATION_JSON).body(userA).when().put("/user").then().statusCode(200)
                .body(is("User erfolgreich aktualisiert"));
    }

    @Test
    @Order(8)
    void TestinvalideUpdate1() {
        userB.setUserName("newUserName2");
        userB.setEmail("newEmail2");
        given().contentType(MediaType.APPLICATION_JSON).body(userB).when().put("/user").then().statusCode(200)
                .body(is("Email und UserName bereits vergeben"));
    }
    @Test
    @Order(9)   
    void TestinvalideUpdate2() {
        userB.setUserName("newUserName2");
        userB.setEmail("newEmail345");
        given().contentType(MediaType.APPLICATION_JSON).body(userB).when().put("/user").then().statusCode(200)
                .body(is("Username bereits vergeben"));
    }

    @Test
    @Order(10)   
    void TestinvalideUpdate3() {
        userB.setUserName("newUserName2456456");
        userB.setEmail("newEmail2");
        given().contentType(MediaType.APPLICATION_JSON).body(userB).when().put("/user").then().statusCode(200)
                .body(is("Email bereits vergeben"));

    }

    @Test
    @Order(11)
    void TestAddAddress() {
        userA.setAddress(addA);
        CheckUserAddress(GetUser(0L, userA.getUserName()),addA);
    }

    @Test
    @Order(12)
    void TestUpdateAddress() {
        addA.setPostalcode(12345L);
        addA.setStreet("TestingRoad");
        userA.setAddress(addA);
        CheckUserAddress(GetUser(0L, userA.getUserName()),addA);
    }

    // @Test
    // @Order(10)
    // void TestAddPhone() {
    // }

    // @Test
    // @Order(11)
    // void TestUpdatePhone() {
    // }

}
