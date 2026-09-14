package rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import bio.cosy.feddb.local.api.cohort.patient.traceability.query.QueryPatientService;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(QueryPatientService.class)
public class QueryPatientServiceTest {

    private static final Long TEST_COHORT_ID = 5L;
    private static final Long TEST_PATIENT_ID = 1L;
    private static final Long TEST_QUERY_ID = 1L;

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueriesAll() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(2));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueriesCohortId() {
        given()
                .queryParam("cohort_id", TEST_COHORT_ID)
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueriesPatientId() {
        given()
                .queryParam("patient_id", TEST_PATIENT_ID)
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueriesQueryId() {
        given()
                .queryParam("query_id", TEST_QUERY_ID)
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueriesCombined() {
        given()
                .queryParam("cohort_id", TEST_COHORT_ID)
                .queryParam("patient_id", TEST_PATIENT_ID)
                .queryParam("query_id", TEST_QUERY_ID)
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(1));
    }


}
