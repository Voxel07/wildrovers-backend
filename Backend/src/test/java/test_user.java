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
import model.*;

import java.util.Arrays;
import java.util.List;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class test_user {

    private static User userA = new User("voxel@web.de", "Camo", "123", "Matthias", "Schnedier", "Vorstand", true);
    private static User userB = new User("matze.schneider95@web.de", "Voxel", "123", "Ralf", "Müller", "Mitglied",
            false);

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
        System.out.println("ID:" + id + " Username: " + username);
        if (id != 0 && username == "") {
            Response resp = given().queryParam("userId", id).contentType(MediaType.APPLICATION_JSON).when()
                    .get("/user");
            resp.then().statusCode(200);
            return Arrays.asList(resp.getBody().as(User[].class)).get(0);
        } else {
            Response resp = given().queryParam("username", username).contentType(MediaType.APPLICATION_JSON).when()
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
        System.out.println("Setting ID:" + ausDB.getId() + " toUsername: " + javaObj.getUserName());
        javaObj.setId(ausDB.getId());
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

}
