package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.store.StoreRatingCreateDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.app.FederatedAppService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.instancio.Select.field;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(FederatedAppService.class)
public class FederatedAppServiceTest {

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    public void testListApps() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testListAppsAuth() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0));
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    public void testGetApp() {
        int id = 1;
        given()
                .pathParam("id", id)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(1));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testGetAppNotFund() {
        Long inValidId = 8888888L;
        given()
                .pathParam("id", inValidId)
                .when().get("{id}")
                .then()
                .statusCode(404);
    }

    //TODO
    /*@Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testCreateApp() {
        FederatedAppCreateDTO newApp = genDTO();


        given()
                .contentType(ContentType.JSON)
                .body(newApp)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("name", is(newApp.getName()));
    }*/

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testCreateAppInvalidData() {
        FederatedAppDTO invalidApp = new FederatedAppDTO();

        given()
                .contentType(ContentType.JSON)
                .body(invalidApp)
                .when().post()
                .then()
                .statusCode(400);
    }

    //TODO
    /*@Test@Test
    @TestSecurity(user = "test", roles = {"admin", "site-admin"})
    public void testUpdateApp() {
        Long appId = 1L;
        FederatedAppDTO updatedApp = federatedAppBO.getById(appId);
        updatedApp.setCertificationLevel(2);
        given()
                .contentType(ContentType.JSON)
                .body(updatedApp)
                .when().put("/" + appId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", is(updatedApp.getName()));
    }*/

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testUpdateAppInvalidData() {
        Long appId = 1L;
        FederatedAppDTO invalidApp = new FederatedAppDTO();

        given()
                .contentType(ContentType.JSON)
                .body(invalidApp)
                .when().put("/" + appId)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin", "site-admin"})
    public void testDeleteApp() {
        Long appId = 3L;

        given()
                .when().delete("/" + appId)
                .then()
                .statusCode(200);
    }


    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testDeleteAppNotFound() {
        long invalidAppId = 999L;

        given()
                .when().delete("/" + invalidAppId)
                .then()
                .statusCode(404);
    }


    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testGetTags() {
        given()
                .when().get("/tags")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0));
    }

    /*@Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testCreateVersion() {
        FederatedAppVersionDTO newVersion = new FederatedAppVersionDTO();
        newVersion.setAppVersion("1.0");
        newVersion.setCertificationLevel(0);
        newVersion.setFederatedAppId(1L);


        given()
                .contentType(ContentType.JSON)
                .body(newVersion)
                .when().post("/version")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("appVersion", is("1.0"));
    }


    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testCreateVersionInvalidData() {
        FederatedAppVersionDTO invalidVersion = new FederatedAppVersionDTO();

        given()
                .contentType(ContentType.JSON)
                .body(invalidVersion)
                .when().post("/version")
                .then()
                .statusCode(415);
    }
*/
    /*@Test
    public void testCreateAttachment() {
        FederatedAppAttachmentDTO attachmentDTO = new FederatedAppAttachmentDTO();
        attachmentDTO.setFileName("file.txt");

        given()
                .contentType(ContentType.MULTIPART)
                .multiPart("file", attachmentDTO.getFileName(), "file content".getBytes())
                .when().post("/version")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("fileName", is("file.txt"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testCreateAttachmentInvalidData() {
        FederatedAppAttachmentDTO invalidAttachmentDTO = new FederatedAppAttachmentDTO();

        given()
                .contentType(ContentType.MULTIPART)
                .multiPart("file", "", "".getBytes())
                .when().post("/version")
                .then()
                .statusCode(400);
    }*/

   //TODO
   /*@Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testGetReadMe() {
        String url = "https://raw.githubusercontent.com/quarkusio/quarkus/refs/heads/main/README.md";

        given()
                .queryParam("url", url)
                .when().get("/readme")
                .then()
                .statusCode(200);
    }*/

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "site-admin"})
    public void testGetReadMeInvalidData() {
        //200 cause default
        given()
                .when().get("/readme")
                .then()
                .statusCode(404);
    }


    private FederatedAppDTO genDTO(Long id) {
        return Instancio.of(FederatedAppDTO.class)
                .set(field(FederatedAppDTO::getId), id)
                .set(field(FederatedAppDTO::getSlug), UUID.randomUUID().toString())
                .set(field(FederatedAppDTO::getTags), genTag())
                .create();
    }

    private StoreRatingCreateDTO genDTO() {
        return Instancio.of(StoreRatingCreateDTO.class)
                .set(field(StoreRatingCreateDTO::getSlug), UUID.randomUUID().toString())
                .set(field(StoreRatingCreateDTO::getTags), genTag())
                .create();
    }

    private Set<FederatedAppDTO> genTag() {
        Set<FederatedAppDTO> tags = new HashSet<>();
        FederatedAppDTO tag = new FederatedAppDTO();
        tag.setName("Test");
        tag.setSlug("test");
        tags.add(tag);
        FederatedAppDTO tag2 = new FederatedAppDTO();
        tag.setName("Test");
        tag.setSlug("test");
        tag.setId(1L);
        tags.add(tag2);
        return tags;
    }
}
