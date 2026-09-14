package de.unihamburg.daibetes;

import de.unihamburg.daibetes.api.umls.UMLSSources;
import de.unihamburg.daibetes.api.umls.search.UMLSIdSourceDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UMLSServiceTest {

    private static final String KNOWN_CONCEPT_ID = "363346000";

    @Test
    @Order(2)
    void testSearchUmlsConcepts() {
        given()
                .queryParam("search_string", "diabetes")
                .queryParam("page", 1)
                .queryParam("page_size", 10)
                .queryParam("sources", UMLSSources.SNOMEDCT_US)
                .when()
                .get("/umls/search")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(3)
    void testGetUmlsItemNotFound() {
        given()
                .queryParam("source", UMLSSources.SNOMEDCT_US)
                .when()
                .get("/umls/NON_EXISTING_ID_12345")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(4)
    void testGetUmlsItemSuccessForKnownConcept() {
        given()
                .queryParam("source", UMLSSources.SNOMEDCT_US)
                .when()
                .get("/umls/" + KNOWN_CONCEPT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }


    @Test
    @Order(5)
    void testCreateCytoscapeGraphForKnownConcept() {
        given()
                .queryParam("source", UMLSSources.SNOMEDCT_US)
                .when()
                .get("/umls/" + KNOWN_CONCEPT_ID + "/cytoscape-graph")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(6)
    void testCreateAllParentsGraphForKnownConcept() {
        UMLSIdSourceDTO body = new UMLSIdSourceDTO();
        body.setId(KNOWN_CONCEPT_ID);
        body.setSource(UMLSSources.SNOMEDCT_US);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/umls/all-parents-graph")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body(not(emptyOrNullString()));
    }
}
