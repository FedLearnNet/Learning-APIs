package de.unihamburg.daibetes;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.datatype.DummyDataRequestDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.CreateOntologyDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationType;
import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
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
class DataTypeServiceTest {

    @Test
    @Order(1)
    void testCreateThenGetDataTypeWithoutEdges() {
        DataTypeNodeDTO dto = createDataTypeDTO(null, null);

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/datatype")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .body("name", equalTo("TestDataType"))
                        .extract()
                        .path("id")
        );

        given()
                .when()
                .get("/datatype/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(createdId.toString()))
                .body("name", equalTo("TestDataType"))
                .body("ontologyIds", anyOf(nullValue(), hasSize(0)))
                .body("schemaIds", anyOf(nullValue(), hasSize(0)));
    }

    @Test
    @Order(2)
    void testCreateDataTypeWithOntologyIds() {
        UUID ontologyId = createTestOntology();

        DataTypeNodeDTO dto = createDataTypeDTO(
                List.of(ontologyId.toString()),
                null
        );

        UUID createdId = UUID.fromString(
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

        given()
                .when()
                .get("/datatype/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ontologyIds", hasItem(ontologyId.toString()));

        given()
                .queryParam("ontologyId", ontologyId)
                .when()
                .get("/datatype")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("results", not(empty()))
                .body("results.id", hasItem(createdId.toString()));
    }

    @Test
    @Order(3)
    void testCreateDataTypeWithSchemaIds() {
        String schemaId = UUID.randomUUID().toString();

        DataTypeNodeDTO dto = createDataTypeDTO(
                null,
                List.of(schemaId)
        );

        UUID createdId = UUID.fromString(
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

        given()
                .when()
                .get("/datatype/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("schemaIds", hasItem(schemaId));
    }

    @Test
    @Order(4)
    void testCreateDataTypeWithOntologyAndSchemaIds() {
        UUID ontologyId = createTestOntology();
        String schemaId = UUID.randomUUID().toString();

        DataTypeNodeDTO dto = createDataTypeDTO(
                List.of(ontologyId.toString()),
                List.of(schemaId)
        );

        UUID createdId = UUID.fromString(
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

        given()
                .when()
                .get("/datatype/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ontologyIds", hasItem(ontologyId.toString()))
                .body("schemaIds", hasItem(schemaId));
    }

    @Test
    @Order(5)
    void testListDatatypesEndpoint() {
        given()
                .when()
                .get("/datatype")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(6)
    void testListDetailedByDataTypeIds() {
        DataTypeNodeDTO dto = createDataTypeDTO(null, null);

        UUID createdId = UUID.fromString(
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

        given()
                .queryParam("dataTypeIds", createdId.toString())
                .when()
                .get("/datatype/detailed")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("results", notNullValue())
                .body("results", not(empty()))
                .body("results[0].id", equalTo(createdId.toString()));
    }

    @Test
    @Order(7)
    void testListForQueryWithOntologyIds() {
        UUID ontologyId = createTestOntology();

        DataTypeNodeDTO dto = createDataTypeDTO(
                List.of(ontologyId.toString()),
                null
        );

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/datatype")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .extract()
                        .path("id")
        );

        given()
                .queryParam("ontology-ids", ontologyId.toString())
                .when()
                .get("/datatype/query")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }


    @Test
    @Order(8)
    void testGenerateDummyDataJson() {
        DummyDataRequestDTO dto = new DummyDataRequestDTO(
                List.of(createOntologyAndDatatypeForDummyGen()),
                5,
                false,
                false
        );
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/datatype/dummy-data")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", not(empty()));
    }

    @Test
    @Order(9)
    void testGenerateDummyDataCsv() {
        DummyDataRequestDTO dto = new DummyDataRequestDTO(
                List.of(createOntologyAndDatatypeForDummyGen()),
                3,
                true,
                true
        );
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/datatype/dummy-data")
                .then()
                .statusCode(200)
                .contentType("text/csv")
                .body(not(emptyOrNullString()));
    }

    @Test
    @Order(10)
    void testUpdateDataType() {
        DataTypeNodeDTO dto = createDataTypeDTO(null, null);

        UUID createdId = UUID.fromString(
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

        DataTypeNodeDTO updateDto = new DataTypeNodeDTO();
        updateDto.setId(createdId);
        updateDto.setName("UpdatedDataType");
        updateDto.setDescription("Updated description");
        updateDto.setType(DataTypes.STRING);

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/datatype/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("UpdatedDataType"))
                .body("description", equalTo("Updated description"));

        given()
                .when()
                .get("/datatype/" + createdId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("UpdatedDataType"))
                .body("description", equalTo("Updated description"));
    }

    @Test
    @Order(11)
    void testDeleteDataType() {
        DataTypeNodeDTO dto = createDataTypeDTO(null, null);

        UUID createdId = UUID.fromString(
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

        given()
                .when()
                .delete("/datatype/" + createdId)
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/datatype/" + createdId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(12)
    void testGenerateDummyDataForNonExistingDatatypeReturns404() {
        String nonExistingDatatypeId = UUID.randomUUID().toString();

        DummyDataRequestDTO dto = new DummyDataRequestDTO(
                List.of(createDummyFeature(new SelectedDataIdsDTO(nonExistingDatatypeId, nonExistingDatatypeId))),
                5,
                false,
                false
        );
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/datatype/dummy-data")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(13)
    void testCheckValidationsForDatatypeWithDummyValue() {
        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName("ValidationTestDataType");
        dto.setDescription("Datatype for validation tests");
        dto.setType(DataTypes.STRING);
        dto.setValidations(addSomeValidations());

        UUID createdId = UUID.fromString(
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

        given()
                .queryParam("value", "dummy-validation-value")
                .when()
                .get("/datatype/" + createdId + "/check-validations")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(14)
    void testListDatatypesNoFilter() {
        given()
                .when()
                .get("/datatype")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(15)
    void testListDetailedBySchemaId() {
        SchemaNodeDTO schemaDto = new SchemaNodeDTO();
        schemaDto.setName("DT Test Root Schema");
        schemaDto.setDescription("Root schema for datatype schemaId tests");
        schemaDto.setType(SchemaNodeType.ATOMIC_ATTRIBUTE);

        UUID schemaId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(schemaDto)
                        .when()
                        .post("/schemas")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );

        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName("SchemaLinkedDataType");
        dto.setDescription("Datatype linked to schema");
        dto.setType(DataTypes.STRING);
        dto.setSchemaIds(List.of(schemaId.toString()));

        UUID createdId = UUID.fromString(
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

        given()
                .queryParam("schemaId", schemaId.toString())
                .when()
                .get("/datatype/detailed")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("results", not(empty()))
                .body("results.id", hasItem(createdId.toString()));
    }

    @Test
    @Order(16)
    void testListDetailedNoFilter() {
        given()
                .when()
                .get("/datatype/detailed")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue());
    }

    @Test
    @Order(17)
    void testUpdateDataTypeWithEmptyDtoIdReturns400() {
        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName("UpdateEmptyIdDataType");
        dto.setDescription("Will be used for empty-id update test");
        dto.setType(DataTypes.STRING);

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/datatype")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .extract()
                        .path("id")
        );

        DataTypeNodeDTO updateDto = new DataTypeNodeDTO();
        updateDto.setName("ShouldFail");
        updateDto.setDescription("DTO id is null");
        updateDto.setType(DataTypes.STRING);

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/datatype/" + createdId)
                .then()
                .statusCode(400);
    }

    @Test
    @Order(18)
    void testUpdateDataTypeWithMismatchingIdReturns400() {
        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName("UpdateMismatchDataType");
        dto.setDescription("Will be used for id-mismatch test");
        dto.setType(DataTypes.STRING);

        UUID createdId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/datatype")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .extract()
                        .path("id")
        );

        UUID otherId = UUID.randomUUID();

        DataTypeNodeDTO updateDto = new DataTypeNodeDTO();
        updateDto.setId(createdId);
        updateDto.setName("ShouldAlsoFail");
        updateDto.setDescription("ID mismatch");
        updateDto.setType(DataTypes.STRING);

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when()
                .put("/datatype/" + otherId)
                .then()
                .statusCode(400);
    }

    private DataTypeNodeDTO createDataTypeDTO(List<String> ontologyIds, List<String> schemaIds) {
        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName("TestDataType");
        dto.setDescription("A test datatype");
        dto.setType(DataTypes.STRING);

        if (ontologyIds != null) {
            dto.setOntologyIds(ontologyIds);
        }
        if (schemaIds != null) {
            dto.setSchemaIds(schemaIds);
        }

        dto.setValidations(addSomeValidations());

        return dto;
    }

    private PatientExportFeatureDTO createOntologyAndDatatypeForDummyGen() {
        UUID ontologyId = createTestOntology();

        DataTypeNodeDTO dto = createDataTypeDTO(
                List.of(ontologyId.toString()),
                null
        );

        UUID dataTypeId = UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(dto)
                        .when()
                        .post("/datatype")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id")
        );

        return createDummyFeature(new SelectedDataIdsDTO(ontologyId.toString(), dataTypeId.toString()));
    }

    private PatientExportFeatureDTO createDummyFeature(SelectedDataIdsDTO selectedDataIdsDTO) {
        PatientExportFeatureDTO featureDTO = new PatientExportFeatureDTO();
        featureDTO.setName("dummyFeature");
        featureDTO.setAllowedDataIds(List.of(selectedDataIdsDTO));
        featureDTO.setTargetDatatypeId(selectedDataIdsDTO.getGlobalDataTypeId());
        return featureDTO;
    }

    private List<DataTypeValidationDTO> addSomeValidations() {
        DataTypeValidationDTO validation2 = new DataTypeValidationDTO();
        validation2.setName(DataTypeValidationType.MINLENGTH);
        validation2.setValidator("3");
        validation2.setMessage("Minimum length is 3 characters");

        DataTypeValidationDTO validation3 = new DataTypeValidationDTO();
        validation3.setName(DataTypeValidationType.MAXLENGTH);
        validation3.setValidator("50");
        validation3.setMessage("Maximum length is 50 characters");

        return List.of(validation2, validation3);
    }

    private UUID createTestOntology() {
        OntologyNodeDTO node = new OntologyNodeDTO();
        node.addName("DT Test Ontology");
        node.setDescription("Ontology for datatype tests");
        node.addCode("DT-ONT");
        node.addSab("SNOMEDCT_US");
        node.setCui("C7654321");
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
}
