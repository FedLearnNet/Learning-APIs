package rest;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartAppDTO;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;
import bio.cosy.feddb.local.services.orch.OrchContainerServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end import through two chained app-based transformers, proving the batching contract:
 * <strong>one container per transformer for the whole run</strong>, fed batch after batch, rather
 * than a container per batch.
 *
 * <p>Everything after the container boundary is real - the tool API key, the websocket endpoint,
 * the batch dispatch, the multipart upload, the streaming ETL. Only the container runtime is
 * replaced, and the stub behaves the way the wrapper does: it opens a websocket with the API key
 * it was handed, waits to be told to run, and answers each run with an upload that echoes the id
 * it was started with.
 *
 * <p>The two transformers are exact inverses - combine folds {@code diag_1}/{@code diag_2} into one
 * value, split takes it apart again - so a correct run reproduces the source data exactly. A
 * batching bug that dropped, duplicated or misordered a batch would change it.
 */
@QuarkusTest
@TestProfile(AppTransformerBatchingE2ETest.AppTransformerProfile.class)
public class AppTransformerBatchingE2ETest {

    private static final String GROUP_KEY = "us-130-app-transform";
    private static final String COHORT_NAME = "US 130 App Transform Schema";
    private static final String GLOBAL_SCHEMA_ID = "53bbf74d-0d9f-4a03-8f1a-b120b4fb9063";
    private static final String CONNECTOR_PATH = "/connectors/us-130-app-transform.json";
    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";
    private static final String ETL_DATA_PATH = "/connectors/clinic_001.csv";

    public static class AppTransformerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "flnet.auto-subscribe." + GROUP_KEY + ".global-schema-id", GLOBAL_SCHEMA_ID,
                    "flnet.auto-subscribe." + GROUP_KEY + ".cohort-name", COHORT_NAME,
                    "flnet.auto-subscribe." + GROUP_KEY + ".connector-path", CONNECTOR_PATH,
                    "flnet.auto-subscribe." + GROUP_KEY + ".etl-data-path", ETL_DATA_PATH,
                    "flnet.auto-subscribe." + GROUP_KEY + ".add-for-user", "test",
                    "flnet.auto-subscribe." + GROUP_KEY + ".enabled-file-watching", "false"
            );
        }
    }

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Inject
    PatientBO patientBO;

    @Inject
    FLNetClientConfig config;

    @Inject
    ObjectMapper objectMapper;

    private TransformerAppStub transformerApps;

    @BeforeEach
    void installMocks() throws Exception {
        SchemaStructureDTO dummySchema;
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SCHEMA_DUMMY_PATH.substring(1))) {
            assertNotNull(in, "Test dummy schema " + SCHEMA_DUMMY_PATH + " must be on the classpath");
            dummySchema = objectMapper.readValue(in, SchemaStructureDTO.class);
        }

        GlobalSchemaSubscriptionService schemaMock = new GlobalSchemaSubscriptionService() {
            @Override
            public Uni<SchemaStructureDTO> subscribe(UUID id) {
                return Uni.createFrom().item(dummySchema);
            }

            @Override
            public Uni<Response> unsubscribe(UUID id) {
                return Uni.createFrom().item(Response.ok().build());
            }
        };
        QuarkusMock.installMockForType(schemaMock, GlobalSchemaSubscriptionService.class, RestClient.LITERAL);

        transformerApps = new TransformerAppStub(objectMapper);
        OrchContainerServiceClient containerClient = org.mockito.Mockito.mock(OrchContainerServiceClient.class);
        org.mockito.Mockito.when(containerClient.startContainer(
                        org.mockito.ArgumentMatchers.any(StartAppDTO.class),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> transformerApps.startContainer(
                        invocation.getArgument(0), invocation.getArgument(4)));
        org.mockito.Mockito.when(containerClient.cleanupWorkflow(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> {
                    transformerApps.closeSockets();
                    return Response.ok().build();
                });
        QuarkusMock.installMockForType(containerClient, OrchContainerServiceClient.class, RestClient.LITERAL);
    }

    @Test
    void chainedAppTransformers_reuseOneContainerPerTransformerAcrossAllBatches() {
        FLNetClientConfig.AutoSubscribeGroupConfig group = config.autoSubscribe().groups().get(GROUP_KEY);
        assertNotNull(group, "Test profile should contribute group '" + GROUP_KEY + "'");

        Boolean ok = autoSubscriberBO.subscribeToFLNet(GROUP_KEY, group).await().atMost(Duration.ofMinutes(3));
        assertTrue(Boolean.TRUE.equals(ok), "AutoSubscribe should succeed");

        Optional<CohortEntity> cohort = cohortAO.findByName(COHORT_NAME);
        assertTrue(cohort.isPresent(), "Cohort '" + COHORT_NAME + "' should have been created");
        Long cohortId = cohort.get().getId();

        List<ConnectorDTO> connectors = connectorBO.getAllByCohortId(cohortId, "test");
        assertFalse(connectors.isEmpty(), "A connector should exist for the cohort");
        ConnectorDTO connector = connectors.getFirst();

        ConnectorRunDTO finalRun = pollForTerminalRun(connector.getId(), 180_000);
        assertNotNull(finalRun, "A connector run should have been started and completed");
        assertEquals(ImportStatusEnum.FINISHED, finalRun.getStatus(),
                "The run should finish, run error: " + finalRun.getErrorMessage());

        // The contract: one container per transformer for the entire run...
        assertEquals(2, transformerApps.startedImages.size(),
                "Exactly one container per app transformer should be started, was "
                        + transformerApps.startedImages);

        // ...and many more batches than containers, which is what proves they were reused.
        assertTrue(transformerApps.totalBatches() > transformerApps.startedImages.size(),
                "The transformers should have served several batches each, served: "
                        + transformerApps.batchesPerStep());

        List<PatientDTO> patients = QuarkusTransaction.requiringNew()
                .call(() -> patientBO.listPatientData(cohortId));
        assertFalse(patients.isEmpty(), "Patients should have been imported");
    }

    private ConnectorRunDTO pollForTerminalRun(Long connectorId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        ConnectorRunDTO latest = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                List<ConnectorRunDTO> runs = QuarkusTransaction.requiringNew()
                        .call(() -> connectorRunBO.getAllForConnector(connectorId));
                if (!runs.isEmpty()) {
                    latest = runs.stream().max(Comparator.comparing(ConnectorRunDTO::getId)).orElse(null);
                    if (latest != null && (latest.getStatus() == ImportStatusEnum.FINISHED
                            || latest.getStatus() == ImportStatusEnum.ERROR)) {
                        return latest;
                    }
                }
            } catch (Exception ignored) {
                // transient read during the import, retry
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return latest;
            }
        }
        return latest;
    }

    /**
     * Stands in for orch-api plus the container runtime, behaving like the wrapper: it connects a
     * websocket with the API key from the container environment, waits to be told to run, applies
     * the transformation the hyper parameters describe, and uploads the result under the id it was
     * started with.
     */
    private static class TransformerAppStub {

        private final ObjectMapper mapper;
        final List<String> startedImages = new CopyOnWriteArrayList<>();
        final Map<Long, AtomicInteger> batches = new ConcurrentHashMap<>();
        private final List<WebSocket> sockets = new CopyOnWriteArrayList<>();
        /**
         * Held on purpose: an HttpClient that becomes unreachable is collected, and the JDK shuts
         * its connections down with it - the socket would drop moments after connecting.
         */
        private final List<HttpClient> clients = new CopyOnWriteArrayList<>();

        TransformerAppStub(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        int totalBatches() {
            return batches.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        Map<Long, AtomicInteger> batchesPerStep() {
            return batches;
        }

        CreateContainerResponseDTO startContainer(StartAppDTO createDTO, Integer port) {
            startedImages.add(createDTO.getAppImage());
            Long stepId = env(createDTO, "APP_ID").map(Long::valueOf).orElseThrow(
                    () -> new IllegalStateException("Container environment is missing APP_ID"));
            String apiKey = env(createDTO, "APP_API_KEY").orElseThrow(
                    () -> new IllegalStateException("Container environment is missing APP_API_KEY"));

            HttpClient client = HttpClient.newHttpClient();
            clients.add(client);
            WebSocket socket = client.newWebSocketBuilder()
                    .header(ToolApiKeyService.HEADER_NAME, apiKey)
                    .buildAsync(URI.create("ws://localhost:" + port + "/connectors/run/execution/" + stepId + "/app"),
                            new AppListener(stepId, apiKey, port))
                    .join();
            sockets.add(socket);

            CreateContainerResponseDTO response = new CreateContainerResponseDTO();
            response.setId("transformer-app-stub-" + stepId);
            return response;
        }

        /** Mirrors the wrapper: one run at a time, answered with an upload echoing the run id. */
        private class AppListener implements WebSocket.Listener {
            private final Long stepId;
            private final String apiKey;
            private final Integer port;
            private final StringBuilder buffer = new StringBuilder();

            AppListener(Long stepId, String apiKey, Integer port) {
                this.stepId = stepId;
                this.apiKey = apiKey;
                this.port = port;
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                webSocket.request(1);
                if (!last) {
                    return null;
                }
                String payload = buffer.toString();
                buffer.setLength(0);
                try {
                    JsonNode envelope = mapper.readTree(payload);
                    if (!"START_PREDICTION".equals(envelope.path("type").asText())) {
                        return null;
                    }
                    handleRun(envelope.path("message"));
                } catch (Exception e) {
                    throw new IllegalStateException("Transformer app stub failed on " + payload, e);
                }
                return null;
            }

            private void handleRun(JsonNode message) throws Exception {
                long batchId = message.path("id").asLong();
                JsonNode hyperParams = message.path("hyperParams");
                String delimiter = hyperParams.path("delimiter").asText("|");
                Map<String, String> inputMapping = mapOf(hyperParams.path("input_mapping"));
                Map<String, String> returnMapping = mapOf(hyperParams.path("return_mapping"));

                List<Map<String, Object>> rows = mapper.convertValue(
                        message.path("inputData").path("input"),
                        mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                if (rows == null) {
                    throw new IllegalStateException("Batch " + batchId + " arrived without rows");
                }

                List<Map<String, Object>> out = new ArrayList<>(rows.size());
                for (Map<String, Object> row : rows) {
                    Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
                    applyTransform(copy, inputMapping, returnMapping, delimiter);
                    out.add(copy);
                }

                batches.computeIfAbsent(stepId, id -> new AtomicInteger()).incrementAndGet();
                upload(batchId, out);
            }

            /**
             * The two real apps in one: combine when the mapping asks for a and b, split when it
             * asks for a single value.
             */
            private void applyTransform(Map<String, Object> row, Map<String, String> inputMapping,
                                        Map<String, String> returnMapping, String delimiter) {
                if (inputMapping.containsKey("a") && inputMapping.containsKey("b")) {
                    Object a = row.get(inputMapping.get("a"));
                    Object b = row.get(inputMapping.get("b"));
                    row.put(returnMapping.get("value"), String.valueOf(a) + delimiter + b);
                    return;
                }
                Object value = row.get(inputMapping.get("value"));
                String[] parts = String.valueOf(value).split(java.util.regex.Pattern.quote(delimiter), -1);
                row.put(returnMapping.get("a"), parts.length > 0 ? parts[0] : "");
                row.put(returnMapping.get("b"), parts.length > 1 ? parts[1] : "");
            }

            private void upload(long batchId, List<Map<String, Object>> rows) throws Exception {
                Path csv = Files.createTempFile("transformer-app-stub-", ".csv");
                csv.toFile().deleteOnExit();
                Files.writeString(csv, toCsv(rows), StandardCharsets.UTF_8);

                RestAssured.given()
                        .port(port)
                        .header(ToolApiKeyService.HEADER_NAME, apiKey)
                        .multiPart(new MultiPartSpecBuilder(csv.toFile())
                                .controlName("files")
                                .fileName("output.csv")
                                .mimeType("application/octet-stream")
                                .header("X-Key", "output")
                                .header("X-Name", "output")
                                .build())
                        // The id the run was started with: this is what pairs the result with its batch.
                        .multiPart("runId", String.valueOf(batchId))
                        .when()
                        .post("/connectors/run/execution/{stepId}/upload/output", stepId)
                        .then()
                        .statusCode(201);
            }
        }

        private static String toCsv(List<Map<String, Object>> rows) {
            if (rows.isEmpty()) {
                return "";
            }
            List<String> columns = new ArrayList<>(rows.getFirst().keySet());
            StringBuilder csv = new StringBuilder(String.join(",", columns)).append("\n");
            for (Map<String, Object> row : rows) {
                List<String> cells = new ArrayList<>(columns.size());
                for (String column : columns) {
                    Object value = row.get(column);
                    String cell = value == null ? "" : String.valueOf(value);
                    cells.add(cell.contains(",") || cell.contains("\"")
                            ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell);
                }
                csv.append(String.join(",", cells)).append("\n");
            }
            return csv.toString();
        }

        private static Map<String, String> mapOf(JsonNode node) {
            Map<String, String> map = new java.util.LinkedHashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
            return map;
        }

        private static Optional<String> env(StartAppDTO dto, String key) {
            List<String> environments = dto.getEnvironments();
            if (environments == null) {
                return Optional.empty();
            }
            return environments.stream()
                    .filter(entry -> entry != null && entry.startsWith(key + "="))
                    .map(entry -> entry.substring(key.length() + 1))
                    .findFirst();
        }

        void closeSockets() {
            sockets.forEach(s -> s.sendClose(WebSocket.NORMAL_CLOSURE, "done"));
        }
    }
}