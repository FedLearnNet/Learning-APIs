package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentAO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantAO;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.Json;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.instancio.Instancio;

import java.util.List;
import java.util.concurrent.CountDownLatch;

//TODO @QuarkusTest
// TODO @TestHTTPEndpoint(ProjectExperimentService.class)
public class FedDBClientLearningQueryTest {


    @Inject
    ProjectFederatedExperimentAO federatedExperimentAO;

    @Inject
    ProjectFederatedExperimentParticipantAO federatedExperimentParticipantAO;
  /*
    @Test
    @TestSecurity(user = "test", roles = "admin")
    public void testLearningQuery() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);

        int result = 10;
        List<Vertx> vertxs = new ArrayList<>();
        List<WebSocketClient> clients = new ArrayList<>();
        try {
            connectOneLearningClient(result, messageLatch, vertxs, clients, null);

            // Fire the learning query. This triggers the WebSocket endpoint on the server.
            //id => project ID
            Response r = given()
                    .pathParam("id", 1L)
                    .contentType(ContentType.JSON)
                    .body(genCreateDTO())
                    .when().post("/federated");


            r.then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .body("id", notNullValue());

            Long experimentId = r.jsonPath().getLong("id");

            // Wait until the websocket message handler has sent the response.
            assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Timeout waiting for websocket message");

            // Now, wait until the asynchronous update in the DB has been applied.
            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> getCurrentQueryResult(experimentId) == result);

            // Final assertion
            given()
                    .pathParam("id", 1L)
                    .pathParam("eId", experimentId)
                    .when().get("federated/{eId}")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("acceptanceCount", is(result))
                    .body("status", is("INIT"));


        } finally {
            clients.getFirst().close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            vertxs.forEach(Vertx::close);
        }
    }


    @Test
    @TestSecurity(user = "test", roles = "admin")
    public void testLearningQueryStatusInit() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(3);

        int result = 10;
        List<Vertx> vertxs = new ArrayList<>();
        List<WebSocketClient> clients = new ArrayList<>();
        try {
            connectOneLearningClient(result, messageLatch, vertxs, clients, null);
            connectOneLearningClient(result, messageLatch, vertxs, clients, null);
            connectOneLearningClient(result, messageLatch, vertxs, clients, null);

            // Fire the learning query. This triggers the WebSocket endpoint on the server.
            //id => project ID
            Response r = given()
                    .pathParam("id", 1L)
                    .contentType(ContentType.JSON)
                    .body(genCreateDTO())
                    .when().post("/federated");


            r.then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .body("id", notNullValue());

            Long experimentId = r.jsonPath().getLong("id");

            // Wait until the websocket message handler has sent the response.
            assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Timeout waiting for websocket message");

            // Now, wait until the asynchronous update in the DB has been applied.
            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> getCurrentQueryResult(experimentId) == result * 3);

            // Final assertion
            given()
                    .pathParam("id", 1L)
                    .pathParam("eId", experimentId)
                    .when().get("federated/{eId}")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("acceptanceCount", is(result * 3))
                    .body("status", is("READY"));
        } finally {
            clients.forEach(c -> {
                try {
                    c.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            vertxs.forEach(Vertx::close);
        }
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    public void testStartLearning() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(3);

        int result = 10;
        List<Vertx> vertxs = new ArrayList<>();
        List<WebSocketClient> clients = new ArrayList<>();
        try {
            connectOneLearningClient(result, messageLatch, vertxs, clients, null);
            connectOneLearningClient(result, messageLatch, vertxs, clients, null);
            connectOneLearningClient(result, messageLatch, vertxs, clients, "mytest");

            // Fire the learning query. This triggers the WebSocket endpoint on the server.
            //id => project ID
            Response r = given()
                    .pathParam("id", 1L)
                    .contentType(ContentType.JSON)
                    .body(genCreateDTO())
                    .when().post("/federated");


            r.then()
                    .statusCode(201)
                    .contentType(ContentType.JSON)
                    .body("id", notNullValue());

            Long experimentId = r.jsonPath().getLong("id");

            // Wait until the websocket message handler has sent the response.
            assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Timeout waiting for websocket message");

            // Now, wait until the asynchronous update in the DB has been applied.
            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> getCurrentQueryResult(experimentId) == result * 3);

            given()
                    .pathParam("id", 1L)
                    .pathParam("eId", experimentId)
                    .contentType(ContentType.JSON)
                    .body(genCreateDTO())
                    .when().put("federated/{eId}/start")
                    .then()
                    .statusCode(200);

            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> statusRunning(experimentId, "mytest"));
            // Final assertion
            given()
                    .pathParam("id", 1L)
                    .pathParam("eId", experimentId)
                    .when().get("federated/{eId}")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("acceptanceCount", is(result * 3))
                    .body("status", is("RUNNING"));
        } finally {
            clients.forEach(c -> {
                try {
                    c.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            vertxs.forEach(Vertx::close);
        }
    }
*/

    /**
     * Helper method that is annotated with @Transactional so that database access
     * occurs in an active transaction even when called from another thread.
     */
    @Transactional
    public Long getCurrentQueryResult(Long leaningId) {
        Long count = federatedExperimentAO.findById(leaningId).getAcceptanceCount();
        if (count == null) {
            return 0L;
        }
        return count;
    }

    @Transactional
    public boolean statusRunning(String leaningId, String uniqueId) {
        return federatedExperimentParticipantAO.findByExperimentIdAndClinicId(leaningId, uniqueId).orElseThrow().getStepStatus().equals(ProjectStatus.RUNNING);
    }

    private CreateProjectFederatedExperimentDTO genCreateDTO() {
        return Instancio.of(CreateProjectFederatedExperimentDTO.class)
                .create();
    }

    private void connectOneLearningClient(int result, CountDownLatch messageLatch,
                                          List<Vertx> vertxs, List<WebSocketClient> clients, String uniqueId) {
        Vertx vertx = Vertx.vertx();
        if (uniqueId == null) {
            uniqueId = "test-" + System.currentTimeMillis();
        }
        LearningQueryClientResponseDTO response = new LearningQueryClientResponseDTO(FedDBClientResponseType.LEARNING_REQUEST, null, uniqueId, result, null, false);
        WebSocketClient client = vertx.createWebSocketClient();
        ObjectMapper objectMapper = new ObjectMapper();
        vertxs.add(vertx);
        clients.add(client);
        String finalUniqueId = uniqueId;
        client.connect(new WebSocketConnectOptions()
                        .setHost("localhost")
                        .setPort(8083)
                        .setURI("query/clients"))
                .onSuccess(ws -> {
                    ws.textMessageHandler(m -> {
                        FedDBClientDataDTO<?> message = Json.decodeValue(m, FedDBClientDataDTO.class);
                        if (message.getMessageType() == FedDBClientTypeEnum.LEARNING_QUERY) {
                            // Convert and update the response with the query id from the received message.
                            ProjectFederatedExperimentForLocalDTO query = objectMapper.convertValue(message.getMessage(), ProjectFederatedExperimentForLocalDTO.class);
                            response.setGlobalFLExperimentUniqueId(query.getGlobalUniqueId());
                            // Send back the response triggering the DB update.
                            ws.writeTextMessage(Json.encode(new FedDBClientDataDTO<>(FedDBClientTypeEnum.LEARNING_QUERY, response)));
                            messageLatch.countDown();
                        }
                        if (message.getMessageType() == FedDBClientTypeEnum.START_LEARNING) {
                            // Convert and update the response with the query id from the received message.
                            StartLearningClientRequestDTO query = objectMapper.convertValue(message.getMessage(), StartLearningClientRequestDTO.class);
                            // Send back the response triggering the DB update.
                            LearningClientSyncResponseDTO r = new LearningClientSyncResponseDTO();
                            r.setType(FedDBClientResponseType.LEARNING_RESPONSE);
                            r.setGlobalUniqueExperimentId(query.getGlobalUniqueLearningExperimentId());
                            r.setCurrentNodeId("1L");
                            r.setStepStatus(ProjectStatus.RUNNING);
                            r.setUniqueRandomClinicId(finalUniqueId);
                            r.setProjectStatus(RunStatusTypes.RUNNING);
                            ws.writeTextMessage(Json.encode(new FedDBClientDataDTO<>(FedDBClientTypeEnum.UPDATE_LEARNING, r)));
                            messageLatch.countDown();
                        }
                    });
                });

    }
}
