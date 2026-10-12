package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.ProjectService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.instancio.Select.all;
import static org.instancio.Select.field;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(ProjectService.class)
public class ProjectServiceTest {

    @Inject
    ProjectBO projectBO;

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    public void testListProjects() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0));
    }

    @Test
    @TestSecurity(user = "keycloak9", roles = {"admin"})
    public void testCreateProject() {
        ProjectCreateDTO newProject = Instancio.create(ProjectCreateDTO.class);
        newProject.setQueryId(1L);
        given()
                .contentType(ContentType.JSON)
                .body(newProject)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testCreateProjectInvalidData() {
        ProjectDTO invalidProject = new ProjectDTO();

        given()
                .contentType(ContentType.JSON)
                .body(invalidProject)
                .when().post()
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    public void testRetrieveProject() {
        int projectId = 1;

        given()
                .when().get("/" + projectId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(projectId));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testRetrieveProjectNotFound() {
        long invalidProjectId = 999L;

        given()
                .when().get("/" + invalidProjectId)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    public void testUpdateProject() {
        long projectId = 1L;
        ProjectDTO updatedProject = projectBO.getById(projectId);
        updatedProject.setStatus(ProjectStatus.READY);

        given()
                .contentType(ContentType.JSON)
                .body(updatedProject)
                .when().put("/" + projectId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", is(updatedProject.getName()));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testUpdateProjectInvalidData() {
        long projectId = 1L;
        ProjectDTO invalidProject = new ProjectDTO();

        given()
                .contentType(ContentType.JSON)
                .body(invalidProject)
                .when().put("/" + projectId)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    public void testDeleteProject() {
        long projectId = 2L;

        given()
                .when().delete("/" + projectId)
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testDeleteProjectNotFound() {
        long invalidProjectId = 999L;

        given()
                .when().delete("/" + invalidProjectId)
                .then()
                .statusCode(404);
    }

    private ProjectDetailDTO genDTO(Long id) {
        return Instancio.of(ProjectDetailDTO.class)
                .ignore(all(List.class))
                .ignore(all(
                        field(ProjectDetailDTO::getVersion),
                        field(ProjectDetailDTO::getCreatedAt),
                        field(ProjectDetailDTO::getUpdatedAt))
                )
                .set(field(ProjectDetailDTO::getId), id)
                .create();
    }
}
