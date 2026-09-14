package de.unihamburg.daibetes;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SubscriptionServiceTest {

    @Test
    @Order(1)
    void testSubscribeSchemaSuccess() {
        UUID schemaId = createHeadSchema();

        given()
                .when()
                .get("/schema/subscriptions/" + schemaId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", notNullValue())
                .body("id", equalTo(schemaId.toString()));
    }

    @Test
    @Order(2)
    void testSubscribeNonExistingSchemaReturns404() {
        UUID randomId = UUID.randomUUID();

        given()
                .when()
                .get("/schema/subscriptions/" + randomId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(3)
    void testUnsubscribeSchemaSuccessAfterSubscribe() {
        UUID schemaId = createHeadSchema();

        given()
                .when()
                .get("/schema/subscriptions/" + schemaId)
                .then()
                .statusCode(200);

        given()
                .when()
                .delete("/schema/subscriptions/" + schemaId)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(4)
    void testUnsubscribeNonExistingSchemaReturns404() {
        UUID randomId = UUID.randomUUID();

        given()
                .when()
                .delete("/schema/subscriptions/" + randomId)
                .then()
                .statusCode(404);
    }

    private UUID createHeadSchema() {
        SchemaNodeDTO headDto = new SchemaNodeDTO();
        headDto.setName("SubscriptionTestRootSchema");
        headDto.setDescription("Root schema node for subscription tests");
        headDto.setType(SchemaNodeType.ROOT);
        return UUID.fromString(
                given()
                        .contentType(ContentType.JSON)
                        .body(headDto)
                        .when()
                        .post("/schemas/head")
                        .then()
                        .statusCode(201)
                        .contentType(ContentType.JSON)
                        .body("id", notNullValue())
                        .extract()
                        .path("id")
        );
    }
}
