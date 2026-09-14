package rest;

import bio.cosy.feddb.local.api.cohort.CohortService;
import bio.cosy.feddb.local.api.cohort.CreateCohortDTO;
import bio.cosy.feddb.local.api.cohort.PublicationStatus;
import bio.cosy.feddb.local.api.cohort.UpdateCohortDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestHTTPEndpoint(CohortService.class)
public class CohortServiceE2ETest {
    final static Long COHORT_TO_DELETE = 4L;
    // cohort 4 has schema nodes 9 and 10 that are only connected to it, so they should be orphaned after deletion

    @Inject
    @RestClient
    GlobalSchemaService globalSchemaService;

    @Inject
    SchemaNodeAO schemaAO;

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListCohorts() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", equalTo(5));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateCohort() {
        CreateCohortDTO createCohortDTO = genDTO();
        createCohortDTO.setGlobalSchemaID("6001");
        createCohortDTO.setName("Test Create Cohort");
        given()
                .contentType(ContentType.JSON)
                .body(createCohortDTO)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue());

        CreateCohortDTO invalidSchema = genDTO();
        invalidSchema.setName("Invalid Cohort");
        invalidSchema.setGlobalSchemaID("9999");
        // Set invalid properties of invalidWorkflow
        given()
                .contentType(ContentType.JSON)
                .body(invalidSchema)
                .when().post()
                .then()
                .statusCode(400);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testCohortNameHealthCheck() {
        given()
                .queryParam("name", "COHORT_001")
                .when().get("health")
                .then()
                .statusCode(200)
                .body("name", equalTo("COHORT_001"))
                .body("nameExists", equalTo(true));

        given()
                .queryParam("name", "Unique Cohort Name For Health Check")
                .when().get("health")
                .then()
                .statusCode(200)
                .body("nameExists", equalTo(false));

        given()
                .queryParam("name", "COHORT_001")
                .queryParam("excludeId", 1)
                .when().get("health")
                .then()
                .statusCode(200)
                .body("nameExists", equalTo(false));

        given()
                .queryParam("name", "COHORT_002")
                .queryParam("excludeId", 1)
                .when().get("health")
                .then()
                .statusCode(200)
                .body("nameExists", equalTo(true));

        given()
                .queryParam("name", "cohort_001")
                .queryParam("excludeId", 1)
                .when().get("health")
                .then()
                .statusCode(200)
                .body("nameExists", equalTo(false));

        given()
                .queryParam("name", "COHORT_002")
                .queryParam("excludeId", 2)
                .when().get("health")
                .then()
                .statusCode(200)
                .body("nameExists", equalTo(false));

        given()
                .queryParam("name", "COHORT_001")
                .queryParam("excludeId", 2)
                .when().get("health")
                .then()
                .statusCode(200)
                .body("nameExists", equalTo(true));

        given()
                .when().get("health")
                .then()
                .statusCode(400);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateCohortAllowsUnchangedName() {
        UpdateCohortDTO updateDTO = new UpdateCohortDTO();
        updateDTO.setName("COHORT_001");
        updateDTO.setDescription("Updated description");
        updateDTO.setStatus(PublicationStatus.DRAFT);

        given()
                .contentType(ContentType.JSON)
                .pathParam("id", 1)
                .body(updateDTO)
                .when().put("{id}")
                .then()
                .statusCode(200)
                .body("name", equalTo("COHORT_001"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateCohortDuplicateNameReturnsConflict() {
        UpdateCohortDTO updateDTO = new UpdateCohortDTO();
        updateDTO.setName("COHORT_002");
        updateDTO.setDescription("Conflict rename");
        updateDTO.setStatus(PublicationStatus.DRAFT);

        given()
                .contentType(ContentType.JSON)
                .pathParam("id", 1)
                .body(updateDTO)
                .when().put("{id}")
                .then()
                .statusCode(409)
                .body(containsString("already exists"))
                .body(containsString("COHORT_002"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateCohortDuplicateNameShowsAdequateError() {
        CreateCohortDTO createCohortDTO = genDTO();
        createCohortDTO.setGlobalSchemaID("6001");
        // This name already exists in import-test/cohort.sql
        createCohortDTO.setName("COHORT_001");

        given()
                .contentType(ContentType.JSON)
                .body(createCohortDTO)
                .when().post()
                .then()
                .statusCode(409)
                .body(containsString("already exists"))
                .body(containsString("COHORT_001"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateCohortWithPartiallyNewSchema() {
        // Verify that the new schema nodes (6001, 6002) do not exist in the database before creation
        List<SchemaNodeEntity> schemaNodesBefore = schemaAO.find("globalId IN ('6001', '6002')").list();
        assertEquals(0, schemaNodesBefore.size(), "Schema nodes 6001 and 6002 should not exist before cohort creation");

        // Create a cohort using the new schema (6001) that is not in the test SQL
        CreateCohortDTO createCohortDTO = genDTO();
        createCohortDTO.setGlobalSchemaID("6001");
        createCohortDTO.setName("Test Create Cohort with New Schema");

        given()
                .contentType(ContentType.JSON)
                .body(createCohortDTO)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue());

        // Verify that the schema nodes are now created in the database
        List<SchemaNodeEntity> schemaNodesAfter = schemaAO.find("globalId IN ('6001', '6002')").list();
        assertEquals(2, schemaNodesAfter.size(), "Schema nodes 6001 and 6002 should be created after cohort creation");

        // Verify that the root node (6001) and its attribute node (6002) exist
        SchemaNodeEntity rootNode = schemaNodesAfter.stream()
                .filter(node -> "6001".equals(node.getGlobalId()))
                .findFirst()
                .orElse(null);
        assertTrue(rootNode != null, "Root node with global ID 6001 should exist");
        assertEquals("NewRootNode", rootNode.getName(), "Root node should have correct name");

        SchemaNodeEntity attributeNode = schemaNodesAfter.stream()
                .filter(node -> "6002".equals(node.getGlobalId()))
                .findFirst()
                .orElse(null);
        assertTrue(attributeNode != null, "Attribute node with global ID 6002 should exist");
        assertEquals("Temperature", attributeNode.getName(), "Attribute node should have correct name");
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testDeleteCohort() {
        // Verify that schema nodes 9 and 10 exist and are connected to cohort 4 before deletion
        List<SchemaNodeEntity> schemaNodesBefore = schemaAO.find("id IN (9, 10)").list();
        assertEquals(2, schemaNodesBefore.size(), "Schema nodes 9 and 10 should exist before cohort deletion");

        // Verify that these nodes are connected to cohort 4
        for (SchemaNodeEntity node : schemaNodesBefore) {
            assertEquals(COHORT_TO_DELETE, node.getCohort().getId(),
                    "Schema node " + node.getId() + " should be connected to cohort " + COHORT_TO_DELETE);
        }

        // Verify that datatypes and ontologies exist before deletion (they should remain after as they're shared)
        Long datatypeCount = schemaAO.count("dataType.globalDataTypeId IN ('1001')", new Object[0]);
        Long ontologyCount = schemaAO.count("ontology.globalId IN ('2001')", new Object[0]);
        assertTrue(datatypeCount > 0, "Datatype with globalId '1001' should exist before cohort deletion");
        assertTrue(ontologyCount > 0, "Ontology with globalId '2001' should exist before cohort deletion");

        // Delete cohort 4
        given()
                .pathParam("id", COHORT_TO_DELETE)
                .when().delete("{id}")
                .then()
                .statusCode(200);

        // Verify that orphaned schema nodes are cleaned up after deletion
        List<SchemaNodeEntity> orphanedNodesAfter = schemaAO.find("cohorts IS EMPTY").list();
        assertEquals(0, orphanedNodesAfter.size(), "All orphaned schema nodes should be cleaned up after cohort deletion");

        // Verify that schema nodes 9 and 10 no longer exist at all
        List<SchemaNodeEntity> schemaNodesAfter = schemaAO.find("id IN (9, 10)").list();
        assertEquals(0, schemaNodesAfter.size(), "Schema nodes 9 and 10 should be completely deleted after cohort deletion");

        // Verify that shared datatypes and ontologies are NOT deleted (they're used by other schema nodes)
        Long datatypeCountAfter = schemaAO.count("dataType.globalDataTypeId IN ('1001')", new Object[0]);
        Long ontologyCountAfter = schemaAO.count("ontology.globalId IN ('2001')", new Object[0]);
        assertTrue(datatypeCountAfter > 0, "Shared datatype with globalId '1001' should still exist after cohort deletion");
        assertTrue(ontologyCountAfter > 0, "Shared ontology with globalId '2001' should still exist after cohort deletion");

        Long invalidId = 999L;
        given()
                .pathParam("id", invalidId)
                .when().delete("{id}")
                .then()
                .statusCode(200);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateCohortWithCompletelyNewSchema() {
        // Verify that schema nodes 7001, 7002, 7003 do not exist in the database before creation
        List<SchemaNodeEntity> schemaNodesBefore = schemaAO.find("globalId IN ('7001', '7002', '7003')").list();
        assertEquals(0, schemaNodesBefore.size(), "Schema nodes with global IDs 7001, 7002, 7003 should not exist before cohort creation");

        // Verify that new datatypes 3001, 3002 do not exist in the database
        Long datatypeCountBefore = schemaAO.count("dataType.globalDataTypeId IN ('3001', '3002')", new Object[0]);
        assertEquals(0, datatypeCountBefore, "Datatypes with global IDs 3001, 3002 should not exist before cohort creation");

        // Verify that new ontologies 4001, 4002 do not exist in the database
        Long ontologyCountBefore = schemaAO.count("ontology.globalId IN ('4001', '4002')", new Object[0]);
        assertEquals(0, ontologyCountBefore, "Ontologies with global IDs 4001, 4002 should not exist before cohort creation");

        // Create a cohort using the completely new schema (7001)
        CreateCohortDTO createCohortDTO = genDTO();
        createCohortDTO.setName("Test Create Cohort with Completely New Schema");
        createCohortDTO.setGlobalSchemaID("7001");

        given()
                .contentType(ContentType.JSON)
                .body(createCohortDTO)
                .when().post()
                .then()
                .statusCode(201)
                .body("id", notNullValue());

        // Verify that new schema nodes are created after cohort creation
        List<SchemaNodeEntity> schemaNodesAfter = schemaAO.find("globalId IN ('7001', '7002', '7003')").list();
        assertEquals(3, schemaNodesAfter.size(), "Schema nodes with global IDs 7001, 7002, 7003 should be created after cohort creation");

        // Verify the root node properties
        SchemaNodeEntity rootNode = schemaNodesAfter.stream()
                .filter(node -> "7001".equals(node.getGlobalId()))
                .findFirst()
                .orElse(null);
        assertTrue(rootNode != null, "Root node with global ID 7001 should exist");
        assertEquals("UniqueRootNode", rootNode.getName(), "Root node should have correct name");

        // Verify the blood pressure attribute node
        SchemaNodeEntity bloodPressureNode = schemaNodesAfter.stream()
                .filter(node -> "7002".equals(node.getGlobalId()))
                .findFirst()
                .orElse(null);
        assertTrue(bloodPressureNode != null, "Blood pressure node with global ID 7002 should exist");
        assertEquals("BloodPressure", bloodPressureNode.getName(), "Blood pressure node should have correct name");

        // Verify the medication attribute node
        SchemaNodeEntity medicationNode = schemaNodesAfter.stream()
                .filter(node -> "7003".equals(node.getGlobalId()))
                .findFirst()
                .orElse(null);
        assertTrue(medicationNode != null, "Medication node with global ID 7003 should exist");
        assertEquals("Medication", medicationNode.getName(), "Medication node should have correct name");

        // Verify that new datatypes are created
        Long datatypeCountAfter = schemaAO.count("dataType.globalDataTypeId IN ('3001', '3002')", new Object[0]);
        assertEquals(2, datatypeCountAfter, "Datatypes with global IDs 3001, 3002 should be created after cohort creation");

        // Verify that new ontologies are created
        Long ontologyCountAfter = schemaAO.count("ontology.globalId IN ('4001', '4002')", new Object[0]);
        assertEquals(2, ontologyCountAfter, "Ontologies with global IDs 4001, 4002 should be created after cohort creation");
    }

    private CreateCohortDTO genDTO() {
        return Instancio.of(CreateCohortDTO.class)
                .ignore(field(CreateCohortDTO.class, "globalSchemaID"))
                .ignore(field(UpdateCohortDTO.class, "name"))
                .create();
    }
}
