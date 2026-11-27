package de.unihamburg.daibetes;

import bio.cosy.feddb.core.security.Roles;
import de.unihamburg.daibetes.api.feature.FeatureAvailableMessages;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

// import files come from umls.import.mrconso/mrrel, set for the test profile
@QuarkusTest
@TestSecurity(user = "test-modeler", roles = {Roles.GLOBAL_DATA_MODELER, Roles.ADMIN})
class UMLSImportServiceTest {

    @Test
    void startsImportWithConfiguredFiles() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/umls")
                .then()
                .statusCode(200);
    }

    @Test
    void rejectsEmbeddingWhenDisabled() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/umls/embedding")
                .then()
                .statusCode(400)
                .body(containsString(FeatureAvailableMessages.EMBEDDING_DISABLED));
    }
}
