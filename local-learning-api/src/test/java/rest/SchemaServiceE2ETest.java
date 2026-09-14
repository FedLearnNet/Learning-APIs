package rest;

import bio.cosy.feddb.local.api.schema.SchemaService;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import org.eclipse.microprofile.rest.client.inject.RestClient;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(SchemaService.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SchemaServiceE2ETest {

    @RestClient
    GlobalSchemaService globalSchemaService;

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testGetGlobalRootNodes() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(3))
                .body("find { it.globalId == '5001' }.name", equalTo("RootNode1"))
                .body("find { it.globalId == '5001' }.description", equalTo("Root node for cohort 1"))
                .body("find { it.globalId == '6001' }.name", equalTo("NewRootNode"))
                .body("find { it.globalId == '6001' }.description", equalTo("New root node for testing schema creation"))
                .body("find { it.globalId == '7001' }.name", equalTo("UniqueRootNode"))
                .body("find { it.globalId == '7001' }.description", equalTo("Completely unique root node for testing full schema creation"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testGetGlobalSchemaFormInfo_ExistingSchema() {
        String existingSchemaId = "5001";

        given()
                .when().get("/{id}/retrieve_dyn_form/", existingSchemaId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("globalId", equalTo(existingSchemaId))
                .body("name", equalTo("RootNode1"))
                .body("description", equalTo("Root node for cohort 1"))
                .body("globalVersion", equalTo(0))
                .body("fields", hasSize(2))
                .body("fields.find { it.schemaNodeId == '5002' }.name", equalTo("Age"))
                .body("fields.find { it.schemaNodeId == '5002' }.description", equalTo("Age node"))
                .body("fields.find { it.schemaNodeId == '5002' }.dataTypeName", equalTo("Int"))
                .body("fields.find { it.schemaNodeId == '5002' }.ontologyName", equalTo("Age (observable entity)"))
                .body("fields.find { it.schemaNodeId == '5003' }.name", equalTo("Diagnosis"))
                .body("fields.find { it.schemaNodeId == '5003' }.description", equalTo("Diagnosis node"))
                .body("fields.find { it.schemaNodeId == '5003' }.dataTypeName", equalTo("String"))
                .body("fields.find { it.schemaNodeId == '5003' }.ontologyName", equalTo("Diagnosis (observable entity)"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testGetGlobalSchemaFormInfo_NonExistentSchema() {
        String nonExistentSchemaId = "9999";

        given()
                .when().get("/{id}/retrieve_dyn_form/", nonExistentSchemaId)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testGetGlobalSchemaFormInfo_EmptySchemaId() {
        given()
                .when().get("/{id}/retrieve_dyn_form/", "")
                .then()
                .statusCode(404);
    }

    @Test
    public void testGetGlobalRootNodes_Unauthenticated() {
        // Test that unauthenticated users cannot access the endpoint
        given()
                .when().get()
                .then()
                .statusCode(401);
    }

    @Test
    public void testGetGlobalSchemaFormInfo_Unauthenticated() {
        // Test that unauthenticated users cannot access the endpoint
        String existingSchemaId = "5001";

        given()
                .when().get("/{id}/retrieve_dyn_form/", existingSchemaId)
                .then()
                .statusCode(401);
    }

}
