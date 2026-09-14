package de.unihamburg.daibetes;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.CreateOntologyDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
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
public class SchemaServiceTest {

    @Test
    @Order(1)
    void testListSchemasEndpoint() {
        given()
                .when()
                .get("/schemas")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(2)
    void testGetHeadSchemasWithoutFilter() {
        given()
                .when()
                .get("/schemas/head")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(3)
    void testCreateHeadSchemaThenGetById() {
        SchemaNodeDTO headDto = createHeadSchemaDTO();

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .body("name", equalTo("TestRootSchema"))
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/schemas/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(createdId.toString()))
                .body("name", equalTo("TestRootSchema"))
                .body("description", equalTo("Root schema node for tests"));
    }


    @Test
    @Order(4)
    void testCreateSchemaNodeThenGetById() {
        SchemaNodeDTO createDto = createSchemaNodePayload();

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(createDto)
                        .when()
                        .post("/schemas")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .body("name", equalTo("TestChildSchema"))
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/schemas/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(createdId.toString()))
                .body("name", equalTo("TestChildSchema"))
                .body("description", equalTo("Child schema node for tests"));
    }


    @Test
    @Order(5)
    void testGetChildrenForSchemaWithoutChildren() {
        SchemaNodeDTO headDto = createHeadSchemaDTO();

        UUID headId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/schemas/" + headId + "/children")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", anyOf(nullValue(), hasSize(0)));
    }

    @Test
    @Order(6)
    void testGetHeadForSchema() {
        SchemaNodeDTO headDto = createHeadSchemaDTO();

        UUID headId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/schemas/" + headId + "/head")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(headId.toString()));
    }

    @Test
    @Order(7)
    void testGetSubStructureForSchema() {
        SchemaNodeDTO headDto = createHeadSchemaDTO();

        UUID headId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/schemas/" + headId + "/sub-structure")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue())
                .body("id", equalTo(headId.toString()));
    }

    @Test
    @Order(8)
    void testUpdateSchemaNode() {
        SchemaNodeDTO headDto = createHeadSchemaDTO();

        UUID schemaId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        SchemaNodeDTO updateDto = new SchemaNodeDTO();
        updateDto.setId(schemaId);
        updateDto.setName("UpdatedSchemaName");
        updateDto.setDescription("Updated schema description");
        updateDto.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/schemas/" + schemaId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("UpdatedSchemaName"))
                .body("description", equalTo("Updated schema description"));

        given()
                .when()
                .get("/schemas/" + schemaId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("UpdatedSchemaName"))
                .body("description", equalTo("Updated schema description"));
    }

    @Test
    @Order(9)
    void testDeleteSchemaNode() {
        SchemaNodeDTO headDto = createHeadSchemaDTO();

        UUID schemaId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .delete("/schemas/" + schemaId)
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/schemas/" + schemaId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(10)
    void testGetSchemaNotFound() {
        UUID randomId = UUID.randomUUID();

        given()
                .when()
                .get("/schemas/" + randomId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(11)
    void testUpdateSchemaNotFound() {
        UUID randomId = UUID.randomUUID();

        SchemaNodeDTO updateDto = new SchemaNodeDTO();
        updateDto.setId(randomId);
        updateDto.setName("DoesNotExist");
        updateDto.setDescription("Should not be found");
        updateDto.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/schemas/" + randomId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(12)
    void testDeleteSchemaNotFound() {
        UUID randomId = UUID.randomUUID();

        given()
                .when()
                .delete("/schemas/" + randomId)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(13)
    void testCreateSchemaInvalidMissingName() {
        SchemaNodeDTO dto = new SchemaNodeDTO();
        dto.setDescription("Missing name should be invalid");
        dto.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/schemas")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(14)
    void testCreateComplexSchemaHierarchyWithOntologyAndDatatype() {
        UUID ontologyId = createOntology();

        UUID dataTypeId = createDataType(ontologyId);

        SchemaNodeDTO root = new SchemaNodeDTO();
        root.setName("ComplexRoot");
        root.setDescription("Complex root schema node");
        root.setType(SchemaNodeType.ROOT);

        UUID rootId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(root)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        SchemaNodeDTO child1 = new SchemaNodeDTO();
        child1.setName("ChildAttr1");
        child1.setDescription("First child attribute");
        child1.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);
        child1.setParentId(rootId);
        child1.setOntologyId(ontologyId);
        child1.setDataTypeId(dataTypeId);

        UUID child1Id = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(child1)
                        .when()
                        .post("/schemas")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        SchemaNodeDTO groupChild = new SchemaNodeDTO();
        groupChild.setName("ChildGroup");
        groupChild.setDescription("Group child under root");
        groupChild.setType(SchemaNodeType.GROUP);
        groupChild.setParentId(rootId);

        UUID groupId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(groupChild)
                        .when()
                        .post("/schemas")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        SchemaNodeDTO groupChild1 = new SchemaNodeDTO();
        groupChild1.setName("GroupChild1");
        groupChild1.setDescription("First child of group");
        groupChild1.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);
        groupChild1.setParentId(groupId);

        UUID groupChild1Id = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(groupChild1)
                        .when()
                        .post("/schemas")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        SchemaNodeDTO groupChild2 = new SchemaNodeDTO();
        groupChild2.setName("GroupChild2");
        groupChild2.setDescription("Second child of group");
        groupChild2.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);
        groupChild2.setParentId(groupId);

        UUID groupChild2Id = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(groupChild2)
                        .when()
                        .post("/schemas")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/schemas/" + rootId + "/sub-structure")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(rootId.toString()));

        given()
                .queryParam("ontologyId", ontologyId)
                .when()
                .get("/schemas/head")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", not(empty()))
                .body("id", hasItem(rootId.toString()));
    }


    private UUID createOntology() {
        OntologyNodeDTO node = new OntologyNodeDTO();
        node.addName("SchemaTest Ontology");
        node.setDescription("Ontology for schema complex tests");
        node.addCode("SCHEMA-ONT");
        node.addSab("SNOMEDCT_US");
        node.setCui("C9999999");
        node.setLat("ENG");

        CreateOntologyDTO dto = new CreateOntologyDTO();
        dto.setOntology(node);
        dto.setEdges(List.of());

        return UUID.fromString(
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
                        .path("nodes[0].id")
        );
    }

    private UUID createDataType(UUID ontologyId) {
        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName("SchemaTestDataType");
        dto.setDescription("Datatype for schema complex tests");
        dto.setType("STRING");
        dto.setOntologyIds(List.of(ontologyId.toString()));

        return UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/datatype")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );
    }

    private SchemaNodeDTO createHeadSchemaDTO() {
        SchemaNodeDTO dto = new SchemaNodeDTO();
        dto.setName("TestRootSchema");
        dto.setDescription("Root schema node for tests");
        dto.setType(SchemaNodeType.ROOT);
        return dto;
    }

    private SchemaNodeDTO createSchemaNodePayload() {
        SchemaNodeDTO dto = new SchemaNodeDTO();
        dto.setName("TestChildSchema");
        dto.setDescription("Child schema node for tests");
        dto.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);
        return dto;
    }
}
