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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

// Pins flnet.federated-learning.platform-aggregator.enabled=false explicitly rather than relying
// on FLNetConfig's literal @WithDefault value, since that default gets flipped locally for manual
// testing of the enabled path.
@QuarkusTest
@TestHTTPEndpoint(ProjectService.class)
@TestProfile(ProjectPlatformAggregatorDisabledTest.PlatformAggregatorDisabledProfile.class)
class ProjectPlatformAggregatorDisabledTest {

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    void platformAggregatorSupportedReturnsFalseWhenDisabled() {
        given()
                .when().get("/platform-aggregator-supported")
                .then()
                .statusCode(200)
                .body(is("false"));
    }

    @Test
    @TestSecurity(user = "keycloak9", roles = {"admin"})
    void createProjectRejectsPlatformIsCoordinatorWhenDisabled() {
        ProjectCreateDTO newProject = new ProjectCreateDTO();
        newProject.setName("Platform Coordinator Project");
        newProject.setDescription("Should be rejected");
        newProject.setQueryId(1L);
        newProject.setPlatformIsCoordinator(true);

        given()
                .contentType(ContentType.JSON)
                .body(newProject)
                .when().post()
                .then()
                .statusCode(405);
    }

    public static class PlatformAggregatorDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "false");
        }
    }
}
