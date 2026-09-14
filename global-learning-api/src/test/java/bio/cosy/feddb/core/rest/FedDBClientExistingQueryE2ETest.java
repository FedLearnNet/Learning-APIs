package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.socket.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.query.QueryBO;
import de.unihamburg.daibetes.api.query.QueryCreateDTO;
import de.unihamburg.daibetes.api.query.QueryService;
import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.Json;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.instancio.Select.all;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestHTTPEndpoint(QueryService.class)
public class FedDBClientExistingQueryE2ETest {

    @TestHTTPResource("/clients")
    URI uri;

    @Inject
    QueryBO queryBO;


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    public void testExistingQuery() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);

        Long result = 10l;
        QueryClientResponseDTO response = new QueryClientResponseDTO(FedDBClientResponseType.QUERY, null, result, null);
        Vertx vertx = Vertx.vertx();
        WebSocketClient client = vertx.createWebSocketClient();
        ObjectMapper objectMapper = new ObjectMapper();


        try {
            Log.infof("Testing existing query connect to host %s, port %s, path %s",
                    uri.getHost(), uri.getPort(), uri.getPath());
            client.connect(new WebSocketConnectOptions()
                            .setHost(uri.getHost())
                            .setPort(uri.getPort())
                            .setURI(uri.getPath()))
                    .onSuccess(ws -> {
                        Log.info("Connected to websocket");
                        ws.textMessageHandler(m -> {
                            FedDBClientDataDTO<?> message = Json.decodeValue(m, FedDBClientDataDTO.class);
                            if (message.getMessageType() == FedDBClientTypeEnum.EXISTING_QUERY) {
                                Log.info(message.toString());
                                // Convert and update the response with the query id from the received message.
                                QueryDTO query = objectMapper.convertValue(message.getMessage(), QueryDTO.class);
                                response.setGlobalUniqueQueryId(query.getGlobalUniqueId());
                                // Send back the response triggering the DB update.
                                ws.writeTextMessage(Json.encode(new FedDBClientDataDTO<>(FedDBClientTypeEnum.EXISTING_QUERY, response)));
                                messageLatch.countDown();
                            }
                        });
                    });

            // Fire the query. This triggers the WebSocket endpoint on the server.
            Response r = given()
                    .contentType(ContentType.JSON)
                    .body(genCreateDTO())
                    .when().post("/fire");


            r.then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("id", notNullValue())
                    .body("result", nullValue())
                    .body("hasFired", is(true));

            Long queryId = r.jsonPath().getLong("id");

            // Wait until the websocket message handler has sent the response.
            assertTrue(messageLatch.await(60, TimeUnit.SECONDS), "Timeout waiting for websocket message");

            // Now, wait until the asynchronous update in the DB has been applied.
            await().atMost(60, TimeUnit.SECONDS)
                    .until(() -> {
                        Integer currentResult = getCurrentQueryResult(queryId);
                        return currentResult != null && currentResult.equals(result.intValue());
                    });

            // Final assertion
            given()
                    .pathParam("id", queryId)
                    .when().get("{id}")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("result", is(result.intValue()));

        } finally {
            client.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            vertx.close();
        }
    }


    /**
     * Helper method that is annotated with @Transactional so that database access
     * occurs in an active transaction even when called from another thread.
     */
    @Transactional
    public Integer getCurrentQueryResult(long queryId) {
        return queryBO.getById(queryId).getResult();
    }

    private QueryCreateDTO genCreateDTO() {
        return Instancio.of(QueryCreateDTO.class)
                .ignore(all(List.class))
                .ignore(all(
                        field(QueryCreateDTO::getVersion),
                        field(QueryCreateDTO::getCreatedAt),
                        field(QueryCreateDTO::getId),
                        field(QueryCreateDTO::getUpdatedAt))
                )
                .set(field(QueryCreateDTO::getProjectIds), Set.of(1L))
                .create();
    }
}
