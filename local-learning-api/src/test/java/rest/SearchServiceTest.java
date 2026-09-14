package rest;

import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.search.SearchBO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
class SearchServiceTest {

    @InjectMock
    SearchBO searchBO;

    @BeforeEach
    void setup() {
        reset(searchBO);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void searchReturnsConnectorResults() {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setId(42L);
        connector.setName("Connector Alpha");
        connector.setDescription("Connector used for alpha imports");
        connector.setCohortId(7L);

        SearchResultDTO<ConnectorDTO> result = new SearchResultDTO<>();
        result.setType(SearchResultType.CONNECTOR);
        result.setTitle(connector.getName());
        result.setResult(connector);
        result.setScore(100);

        List<SearchResultDTO<?>> results = List.of(result);
        when(searchBO.search(eq("alpha"), eq(3), eq("admin"))).thenReturn(results);

        given()
                .queryParam("q", "alpha")
                .queryParam("limit", 3)
                .when()
                .get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(1))
                .body("[0].type", is("CONNECTOR"))
                .body("[0].title", is("Connector Alpha"))
                .body("[0].score", is(100))
                .body("[0].result.id", is(42))
                .body("[0].result.name", is("Connector Alpha"))
                .body("[0].result.cohortId", is(7));

        verify(searchBO).search("alpha", 3, "admin");
    }
}
