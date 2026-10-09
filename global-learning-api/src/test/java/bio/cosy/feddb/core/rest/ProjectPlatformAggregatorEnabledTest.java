package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.ProjectService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

// Pins flnet.federated-learning.platform-aggregator.enabled=true explicitly rather than relying
// on FLNetConfig's literal @WithDefault value, since that default gets flipped locally for manual
// testing of the enabled path.
@QuarkusTest
@TestHTTPEndpoint(ProjectService.class)
@TestProfile(ProjectPlatformAggregatorEnabledTest.PlatformAggregatorEnabledProfile.class)
class ProjectPlatformAggregatorEnabledTest {

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    void platformAggregatorSupportedReturnsTrueWhenEnabled() {
        given()
                .when().get("/platform-aggregator-supported")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    @Test
    @TestSecurity(user = "keycloak9", roles = {"admin"})
    void createProjectAcceptsPlatformIsCoordinatorWhenEnabled() {
        ProjectCreateDTO newProject = new ProjectCreateDTO();
        newProject.setName("Platform Coordinator Project " + UUID.randomUUID());
        newProject.setDescription("Should be accepted");
        newProject.setQueryId(1L);
        newProject.setPlatformIsCoordinator(true);

        given()
                .contentType(ContentType.JSON)
                .body(newProject)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("platformIsCoordinator", is(true));
    }

    public static class PlatformAggregatorEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "true");
        }
    }
}
