package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.project.ProjectAO;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.query.QueryBO;
import de.unihamburg.daibetes.api.query.QueryAO;
import de.unihamburg.daibetes.api.query.QueryCreateDTO;
import de.unihamburg.daibetes.api.query.QueryEntity;
import de.unihamburg.daibetes.api.query.QueryService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestHTTPEndpoint(QueryService.class)
public class QueryServiceTest {

    @Inject
    QueryBO queryBO;

    @Inject
    ProjectAO projectAO;

    @Inject
    QueryAO queryAO;

    @BeforeAll
    public static void setup() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    public void testUpdateCreatesNewVersionAndListReturnsLatestOnly() {
        QueryDTO baseQuery = queryBO.getById(1L, "test");
        baseQuery.setName("Query 1 v2");
        baseQuery.setDescription("Updated description");
        QueryItemDTO updatedItem = new QueryItemDTO();
        updatedItem.setOntologyId("updated-ontology");
        updatedItem.setDataTypeId("updated-datatype");
        updatedItem.setOperator(List.of());
        baseQuery.setQuery(List.of(updatedItem));

        QueryDTO updated = given()
                .contentType(ContentType.JSON)
                .body(baseQuery)
                .when().put("/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("id", not(equalTo(1)))
                .body("groupId", equalTo(baseQuery.getGroupId()))
                .body("globalUniqueId", notNullValue())
                .body("globalUniqueId", not(equalTo(baseQuery.getGlobalUniqueId())))
                .body("hasFired", equalTo(false))
                .body("hasResult", equalTo(false))
                .body("result", nullValue())
                .extract().as(QueryDTO.class);

        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(updated.getId().intValue()))
                .body("[0].groupId", equalTo(baseQuery.getGroupId()));

        given()
                .when().get("/" + updated.getId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(updated.getId().intValue()))
                .body("groupId", equalTo(baseQuery.getGroupId()))
                .body("olderQueries.size()", greaterThanOrEqualTo(1))
                .body("olderQueries.id", hasItem(1))
                .body("olderQueries.groupId", hasItem(baseQuery.getGroupId()));
    }

    @Test
    @TestSecurity(user = "test", roles = {"admin"})
    public void testFireQueryOnExecutedVersionCreatesFreshRerunVersion() {
        QueryDTO rerun = given()
                .contentType(ContentType.JSON)
                .when().post("/1/fire")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", not(equalTo(1)))
                .body("groupId", notNullValue())
                .body("globalUniqueId", notNullValue())
                .body("hasFired", equalTo(true))
                .body("hasResult", equalTo(false))
                .body("result", nullValue())
                .extract().as(QueryDTO.class);

        given()
                .when().get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(rerun.getId().intValue()))
                .body("[0].groupId", equalTo(rerun.getGroupId()));
    }

    @Test
    public void testQueryUpdateDoesNotChangeProjects() {
        Long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            QueryCreateDTO createDTO = new QueryCreateDTO();
            createDTO.setName("Project-safe query");
            createDTO.setDescription("Used to verify project merge isolation");
            createDTO.setQuery(List.of());
            QueryDTO query = queryBO.create(createDTO, "test");

            QueryEntity queryEntity = queryAO.findById(query.getId());
            ProjectEntity project = new ProjectEntity();
            project.setName("Unchanged project");
            project.setDescription("This must survive a query update");
            project.setCertificationLevel(3);
            project.setQuery(queryEntity);
            projectAO.persist(project);

            return new Long[]{query.getId(), project.getId()};
        });

        QueryDTO updated = QuarkusTransaction.requiringNew().call(() -> {
            QueryDTO query = queryBO.getById(ids[0], "test");
            query.setHasFired(true);
            return queryBO.update(query);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            ProjectEntity project = projectAO.findById(ids[1]);
            assertEquals("Unchanged project", project.getName());
            assertEquals("This must survive a query update", project.getDescription());
            assertEquals(3, project.getCertificationLevel());
            assertEquals(0L, project.getVersion());
            assertEquals(ids[0], project.getQuery().getId());
            assertEquals(Set.of(ids[1]), updated.getProjectIds());
        });
    }
}
