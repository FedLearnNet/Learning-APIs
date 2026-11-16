package bio.cosy.feddb.core.rest.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class ModelExperimentResultAuthenticationTest {

    @Test
    void anonymousUploadIsRejected() {
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
}
