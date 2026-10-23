package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.project.ProjectService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
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

    // Coverage for the create-time platform-aggregator gate (now on
    // ProjectFederatedExperimentBO.create(), not ProjectBO.create()) lives in
    // ProjectFederatedExperimentPlatformCoordinatorDisabledTest instead, since creating an
    // experiment requires a project with an attached workflow, which that test already sets up.

    public static class PlatformAggregatorDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "false");
        }
    }
}
