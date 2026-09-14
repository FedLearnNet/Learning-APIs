package rest;

import bio.cosy.feddb.local.api.cohort.permission.CreatePermissionDTO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rest.helper.RestAssuredConfigUtil;


import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.instancio.Select.all;
import static org.instancio.Select.field;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(PermissionService.class)
public class PermissionServiceTest {

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    private static final Long TEST_COHORT_ID = 1L;

    @Inject
    PermissionBO permissionBO;

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListPermission() {
        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", greaterThanOrEqualTo(8));
    } //

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testRetrievePermission() {
        Long validId = 5L;
        given()
                .pathParam("id", validId)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()));

        Long invalidId = 999L;
        given()
                .pathParam("id", invalidId)
                .when().get("{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreatePermission() {
        CreatePermissionDTO newPermission = genCreateDTO();
        newPermission.setCohortId(TEST_COHORT_ID);
        given()
                .contentType(ContentType.JSON)
                .body(newPermission)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue());

        CreatePermissionDTO invalidPermission = new CreatePermissionDTO();
        // no cohortID
        given()
                .contentType(ContentType.JSON)
                .body(invalidPermission)
                .when().post()
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdatePermission() {
        Long validId = 6L;

        PermissionDTO updatePermission = permissionBO.getById(6L);
        updatePermission.setIsAllowedToQuery(false);

        given()
                .contentType(ContentType.JSON)
                .body(updatePermission)
                .pathParam("id", validId)
                .when().put("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()))
                .body("isAllowedToQuery", is(false));


        given()
                .contentType(ContentType.JSON)
                .body(updatePermission)
                .pathParam("id", validId)
                .when().put("{id}")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdatePermissionInvalidId() {
        Long inValidId = 999L;
        PermissionDTO updatePermission = permissionBO.getById(6L);
        updatePermission.setIsAllowedToQuery(false);

        given()
                .contentType(ContentType.JSON)
                .body(updatePermission)
                .pathParam("id", inValidId)
                .when().put("{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testDeletePermission() {
        Long validId = 2L;
        given()
                .pathParam("id", validId)
                .when().delete("{id}")
                .then()
                .statusCode(200);

        Long invalidId = 999L;
        given()
                .pathParam("id", invalidId)
                .when().delete("{id}")
                .then()
                .statusCode(200);
    }

    private PermissionDTO genDTO() {
        return Instancio.of(PermissionDTO.class)
                .ignore(all(
                        field(PermissionDTO::getId),
                        field(PermissionDTO::getVersion),
                        field(PermissionDTO::getCreatedAt),
                        field(PermissionDTO::getUpdatedAt))
                )
                .create();
    }

    private CreatePermissionDTO genCreateDTO() {
        return Instancio.of(CreatePermissionDTO.class)
                .create();
    }
}
