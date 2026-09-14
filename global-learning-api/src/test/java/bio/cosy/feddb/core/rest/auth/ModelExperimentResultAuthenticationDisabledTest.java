package bio.cosy.feddb.core.rest.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
@TestProfile(ModelExperimentResultAuthenticationDisabledTest.AuthDisabledProfile.class)
class ModelExperimentResultAuthenticationDisabledTest {

    @Test
    void anonymousUploadPassesTheAuthenticationGateWhenClientAuthIsDisabled() {
        int status = given()
                .multiPart("files", "model.bin", "model".getBytes(StandardCharsets.UTF_8), "application/octet-stream")
                .multiPart("globalFLExperimentUniqueId", "missing-experiment")
                .multiPart("appVersionId", "999")
                .multiPart("clinicId", "clinic")
                .when()
                .post("/model/result/experiment/upload")
                .statusCode();

        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    public static class AuthDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.oidc.enabled", "false",
                    "flnet.feddb-client.auth.enable", "false"
            );
        }
    }
}
