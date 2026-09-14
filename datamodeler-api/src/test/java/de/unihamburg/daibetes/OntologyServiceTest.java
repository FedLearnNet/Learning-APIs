package de.unihamburg.daibetes;

import bio.cosy.feddb.core.api.datamodler.ontology.CreateOntologyDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OntologyServiceTest {

    @Test
    @Order(1)
    void testCreateThenReadOntology() {

        CreateOntologyDTO dto = createTestDTO();

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/ontology")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("nodes.size()", greaterThan(0))
                        .body("nodes[0].id", notNullValue())
                        .extract()
                        .path("nodes[0].id"));

        given()
                .when()
                .get("/ontology/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(createdId.toString()))
                .body("names", hasItem("TestName"))
                .body("codes", hasItem("T-CODE"))
                .body("sabs", hasItem("SNOMEDCT_US"));
    }


    @Test
    @Order(2)
    void testListOntologiesEndpoint() {
        given()
                .when()
                .get("/ontology")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(3)
    void testListQueryabilityWithoutFilter() {
        given()
                .when()
                .get("/ontology/queryability")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(4)
    void testListQueryabilityWithFilter() {
        given()
                .queryParam("filter_clients", "clientA", "clientB")
                .when()
                .get("/ontology/queryability")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(5)
    void testGetChildrenForOntologyWithoutChildren() {

        CreateOntologyDTO dto = createTestDTO();

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/ontology")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("nodes.size()", greaterThan(0))
                        .body("nodes[0].id", notNullValue())
                        .extract()
                        .path("nodes[0].id"));

        given()
                .when()
                .get("/ontology/" + createdId + "/children")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(0));
    }

    @Test
    @Order(6)
    void testUpdateOntology() {

        CreateOntologyDTO dto = createTestDTO();

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/ontology")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("nodes.size()", greaterThan(0))
                        .body("nodes[0].id", notNullValue())
                        .extract()
                        .path("nodes[0].id"));

        OntologyNodeDTO updateNode = new OntologyNodeDTO();
        updateNode.setId(createdId);
        updateNode.addName("UpdatedName");
        updateNode.setDescription("Updated ontology description");
        updateNode.addCode("U-CODE");
        updateNode.addSab("SNOMEDCT_US");
        updateNode.setCui("C1234567");
        updateNode.setLat("ENG");

        given()
                .contentType(ContentType.JSON)
                .body(updateNode)
                .when()
                .put("/ontology/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("names", hasItem("UpdatedName"))
                .body("description", equalTo("Updated ontology description"));

        given()
                .when()
                .get("/ontology/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("names", hasItem("UpdatedName"))
                .body("description", equalTo("Updated ontology description"));
    }

    @Test
    @Order(7)
    void testDeleteOntology() {

        CreateOntologyDTO dto = createTestDTO();

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/ontology")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("nodes.size()", greaterThan(0))
                        .body("nodes[0].id", notNullValue())
                        .extract()
                        .path("nodes[0].id"));

        given()
                .when()
                .delete("/ontology/" + createdId)
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/ontology/" + createdId)
                .then()
                .statusCode(404);
    }

    private CreateOntologyDTO createTestDTO() {
        OntologyNodeDTO node = new OntologyNodeDTO();
        node.addName("TestName");
        node.setDescription("A test ontology");
        node.addCode("T-CODE");
        node.addSab("SNOMEDCT_US");
        node.setCui("C1234567");
        node.setLat("ENG");

        CreateOntologyDTO dto = new CreateOntologyDTO();
        dto.setOntology(node);
        dto.setEdges(List.of());

        return dto;
    }
}
