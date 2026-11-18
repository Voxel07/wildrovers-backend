package resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class YearlyFeeProfileTest {
    @Inject EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        if (em.find(model.User.class, 960L) == null) {
            em.createNativeQuery("INSERT INTO \"USER\" (id,email,userName,password,firstName,lastName,role,isActive,regestrationDate,canCreateCategory,isBlocked) "
                    + "VALUES (960,'fee-paid@test.local','feePaidUser','test1234','Fee','Paid','Mitglied',true,0,false,false)")
                    .executeUpdate();
        }
        em.createNativeQuery("DELETE FROM YEARLY_FEE WHERE user_id = 960").executeUpdate();
        em.createNativeQuery("INSERT INTO YEARLY_FEE (id, fee_year, user_id) VALUES (96001, :year, 960)")
                .setParameter("year", java.time.Year.now().getValue()).executeUpdate();
    }

    @org.junit.jupiter.api.AfterEach
    @Transactional
    void cleanup() {
        em.createNativeQuery("DELETE FROM YEARLY_FEE WHERE user_id = 960").executeUpdate();
    }

    @Test
    @TestSecurity(user = "feePaidUser", roles = "Mitglied")
    void ownProfileShowsThePaidYearlyFee() {
        int year = java.time.Year.now().getValue();
        given().queryParam("stats", true).get("/user/me").then().statusCode(200)
                .body("hasPaidCurrentYear", equalTo(true))
                .body("paidYears", hasItem(year));
        given().get("/user/me").then().statusCode(200)
                .body("hasPaidCurrentYear", equalTo(true));
    }
}
