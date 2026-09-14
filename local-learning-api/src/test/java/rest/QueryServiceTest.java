package rest;

import bio.cosy.feddb.local.api.query.QueryService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(QueryService.class)
public class QueryServiceTest {

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueries() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(5));
    }


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testRetrieveQuery() {
        Long validId = 1L;
        given()
                .pathParam("id", validId)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()));

        Long invalidId = 999L;
        given()
                .pathParam("id", invalidId)
                .when().get("{id}")
                .then()
                .statusCode(404);
    }
}
