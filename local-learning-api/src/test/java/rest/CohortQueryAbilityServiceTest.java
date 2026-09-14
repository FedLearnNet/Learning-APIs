package rest;

import bio.cosy.feddb.local.api.cohort.queryability.*;
import io.quarkus.test.TestTransaction;
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
import static org.hamcrest.Matchers.equalTo;
import static org.instancio.Select.all;
import static org.instancio.Select.field;

@QuarkusTest
@Transactional
@TestHTTPEndpoint(CohortQueryAbilityService.class)
public class CohortQueryAbilityServiceTest {
    private static final Long TEST_COHORT_ID_GET = 2L;
    private static final Long TEST_COHORT_ID = 1L;
    private static final Long TEST_INVALID_COHORT_ID = 999L;

    @Inject
    CohortQueryAbilityBO cohortQueryAbilityBO;

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testListQueryAbilities() {
        given()
                .pathParam("cohortId", TEST_COHORT_ID_GET)
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(notNullValue())
                .body("size()", equalTo(3));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testRetrieveQueryAbility() {
        Long validId = 1L;
        given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .pathParam("id", validId)
                .when().get("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()));

        Long invalidId = 999L;
        given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .pathParam("id", invalidId)
                .when().get("{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testCreateQueryAbility() {
        CreateCohortQueryAbilityDTO newQueryAbility = genCreateDTO();
        newQueryAbility.setCohortId(TEST_COHORT_ID);
        newQueryAbility.setSchemaNodeId(3L);
        given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .contentType(ContentType.JSON)
                .body(newQueryAbility)
                .when().post()
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue());

        CreateCohortQueryAbilityDTO invalidQueryAbility = new CreateCohortQueryAbilityDTO();
        invalidQueryAbility.setCohortId(TEST_INVALID_COHORT_ID);
        invalidQueryAbility.setSchemaNodeId(2L);
        given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .contentType(ContentType.JSON)
                .body(invalidQueryAbility)
                .when().post()
                .then()
                .statusCode(400);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateQueryAbility() {
        Long validId = 1L;

        CohortQueryAbilityDTO updateQueryAbility = cohortQueryAbilityBO.getById(validId);
        updateQueryAbility.setQueryAbilityInfo(QueryAbility.EXISTENCE);

        given()
                .contentType(ContentType.JSON)
                .body(updateQueryAbility)
                .pathParam("cohortId", validId)
                .pathParam("id", validId)
                .when().put("{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(validId.intValue()))
                .body("queryAbilityInfo", is(QueryAbility.EXISTENCE.toString().toUpperCase()));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testUpdateQueryAbilityInvalidId() {
        Long inValidId = 999L;
        CohortQueryAbilityDTO updateQueryAbility = cohortQueryAbilityBO.getById(TEST_COHORT_ID);
        updateQueryAbility.setQueryAbilityInfo(QueryAbility.EXISTENCE);
        given()
                .contentType(ContentType.JSON)
                .body(updateQueryAbility)
                .pathParam("cohortId", TEST_COHORT_ID)
                .pathParam("id", inValidId)
                .when().put("{id}")
                .then()
                .statusCode(405);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "admin", roles = "admin")
    public void testDeleteQueryAbility() {
        Long validId = 4L;
        given()
                .pathParam("cohortId", 2L)
                .pathParam("id", validId)
                .when().delete("{id}")
                .then()
                .statusCode(200);

        Long invalidId = 999L;
        given()
                .pathParam("cohortId", TEST_COHORT_ID)
                .pathParam("id", invalidId)
                .when().delete("{id}")
                .then()
                .statusCode(200);
    }

//     private CohortQueryAbilityDTO genDTO() {
//         return Instancio.of(CohortQueryAbilityDTO.class)
//                 .ignore(all(
//                         field(CohortQueryAbilityDTO::getId),
//                         field(CohortQueryAbilityDTO::getVersion),
//                         field(CohortQueryAbilityDTO::getCreatedAt),
//                         field(CohortQueryAbilityDTO::getUpdatedAt))
//                 )
//                 .create();
//     }

    private CreateCohortQueryAbilityDTO genCreateDTO() {
        return Instancio.of(CreateCohortQueryAbilityDTO.class)
                .ignore(all(
                        field(CreateCohortQueryAbilityDTO::getId),
                        field(CreateCohortQueryAbilityDTO::getVersion),
                        field(CreateCohortQueryAbilityDTO::getCreatedAt),
                        field(CreateCohortQueryAbilityDTO::getUpdatedAt))
                )
                .create();
    }
}
