package de.unihamburg.daibetes;

import de.unihamburg.daibetes.api.umls.importer.UmlsImportDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UMLSImportServiceTest {

    @Test
    @Order(1)
    void testUmlsImportSuccessWithDefaultPaths() {
        UmlsImportDTO dto = new UmlsImportDTO();

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/umls")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    @Order(2)
    void testUmlsImportFailsWhenNodeFileMissing() {
        UmlsImportDTO dto = new UmlsImportDTO();
        dto.setNodeFileName("umls/FILE_DOES_NOT_EXIST.RRF");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/umls")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    @Order(3)
    void testUmlsImportFailsWhenEdgeFileMissing() {
        UmlsImportDTO dto = new UmlsImportDTO();
        dto.setEdgeFileName("umls/NO_EDGE_FILE.RRF");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/umls")
                .then()
                .statusCode(400)
                .body(notNullValue());
    }

    @Test
    @Order(4)
    void testUmlsImportFailsWhenBothFilesMissing() {
        UmlsImportDTO dto = new UmlsImportDTO();
        dto.setNodeFileName("does/not/exist/MRCONSO.RRF");
        dto.setEdgeFileName("does/not/exist/MRREL.RRF");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/umls")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(5)
    void testUmlsImportFailsWhenDtoEmpty() {
        UmlsImportDTO dto = new UmlsImportDTO();
        dto.setNodeFileName(null);
        dto.setEdgeFileName(null);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/umls")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(6)
    void testUmlsImportFailsWithInvalidFilePaths() {
        UmlsImportDTO dto = new UmlsImportDTO();
        dto.setNodeFileName("   ");  // invalid blank
        dto.setEdgeFileName("!@#$%^&*");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/umls")
                .then()
                .statusCode(400);
    }
}
