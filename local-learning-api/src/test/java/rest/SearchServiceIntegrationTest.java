package rest;

import bio.cosy.feddb.local.api.search.SearchService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
class SearchServiceIntegrationTest {

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void searchRunsAcrossRealSourcesWithoutResultSetCursorFailure() {
        given()
                .queryParam("q", "a")
                .queryParam("limit", 10)
                .when()
                .get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue());
    }
}
