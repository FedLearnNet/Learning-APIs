package bio.cosy.feddb.core.rest.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
@TestProfile(ModelExperimentResultAuthenticationEnabledTest.AuthEnabledProfile.class)
class ModelExperimentResultAuthenticationEnabledTest {

    @Test
    void anonymousUploadIsRejectedWhenClientAuthIsEnabled() {
        givenUpload().when()
                .post("/model/result/experiment/upload")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "clinic")
    void authenticatedUploadPassesTheAuthenticationGate() {
        int status = givenUpload().when()
                .post("/model/result/experiment/upload")
                .statusCode();

        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    private io.restassured.specification.RequestSpecification givenUpload() {
        return given()
                .multiPart("files", "model.bin", "model".getBytes(StandardCharsets.UTF_8), "application/octet-stream")
                .multiPart("globalFLExperimentUniqueId", "missing-experiment")
                .multiPart("appVersionId", "999")
                .multiPart("clinicId", "clinic");
    }

    public static class AuthEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.oidc.enabled", "false",
                    "flnet.feddb-client.auth.enable", "true"
            );
        }
    }
}
