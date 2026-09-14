package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import de.unihamburg.daibetes.api.workflow.WorkflowService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.instancio.Select.all;
import static org.instancio.Select.field;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(WorkflowService.class)
public class WorkflowServiceTest {

    //TODO
   /* @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListWorkflows() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(1));
    }*/

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testRetrieveWorkflow() {
        Long validId = 1L; // Replace with a valid ID
        given()
                .pathParam("id", validId)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()));

        Long invalidId = 999L; // Replace with an invalid ID
        given()
                .pathParam("id", invalidId)
                .when().get("/workflow/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateWorkflow() {
        WorkflowCreateDTO newWorkflow = new WorkflowCreateDTO();
        // Set properties of newWorkflow
        given()
                .contentType(ContentType.JSON)
                .body(newWorkflow)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateWorkflow() {
        Long validId = 1L;
        WorkflowDTO updateWorkflow = genDTO();
        updateWorkflow.setId(validId);

        given()
                .pathParam("id", validId)
                .contentType(ContentType.JSON)
                .body(updateWorkflow)
                .when().put("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateWorkflowInvalidId() {
        Long inValidId = 999L;
        WorkflowDTO updateWorkflow = genDTO();
        updateWorkflow.setId(inValidId);

        given()
                .pathParam("id", inValidId + 1)
                .contentType(ContentType.JSON)
                .body(updateWorkflow)
                .when().put("{id}")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testDeleteWorkflow() {
        Long validId = 10L; // Replace with a valid ID 10 can be deleted
        given()
                .pathParam("id", validId)
                .when().delete("{id}")
                .then()
                .statusCode(200);

        Long invalidId = 999L; // Replace with an invalid ID
        given()
                .pathParam("id", invalidId)
                .when().delete("{id}")
                .then()
                .statusCode(200);
    }

    private WorkflowDTO genDTO() {
        return Instancio.of(WorkflowDTO.class)
                .ignore(all(List.class))
                .ignore(all(
                        field(WorkflowDTO::getId),
                        field(WorkflowDTO::getVersion),
                        field(WorkflowDTO::getCreatedAt),
                        field(WorkflowDTO::getUpdatedAt))
                )
                .set(field(WorkflowDTO::getKeycloakId), "admin")
                .create();
    }
}
