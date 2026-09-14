package rest;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.dto.ContainerDTO;
import bio.cosy.feddb.core.services.orch.dto.ContainerLogDTO;
import bio.cosy.feddb.core.services.orch.dto.ContainerRunDTO;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartAppDTO;
import bio.cosy.feddb.core.services.orch.dto.StartPipelineDTO;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.AppBasedUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepAO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;
import bio.cosy.feddb.local.services.orch.OrchContainerServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end import of the US-130 cohort with <em>no file anywhere in the configuration</em>:
 * the connector's input is the {@code diabetic-130-US-hospital-dataset} extractor app, which ships
 * the dataset inside its image and hands it over by uploading its {@code data} output.
 *
 * <p>Everything after the container boundary is the real system - the tool API key, the multipart
 * upload endpoint, {@link bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionOutputAwaiter},
 * the streaming ETL, the mapping and the patient store. Only the container runtime itself is
 * replaced: {@link ContainerServiceClient#startContainer} stands in for orch-api and docker, and
 * behaves the way the real extractor app does - it reads {@code APP_ID} and {@code APP_API_KEY}
 * out of the container environment and posts its declared outputs back.
 *
 * <p>The stub uploads the app's declared outputs in the order the app returns them - {@code data}
 * (the clinical records) and then {@code ids} (a code lookup table) - so the importer sees the
 * same multipart body the real container sends. Both are expected to land in the cohort's file
 * store, with the connector left pointing at them, which is what lets every step after the
 * extraction treat an app-based connector exactly like a file-based one.
 */
@QuarkusTest
@TestProfile(AppBasedAutoSubscribeE2ETest.AppExtractorProfile.class)
public class AppBasedAutoSubscribeE2ETest {

    private static final String GROUP_KEY = "us-130-app";
    private static final String COHORT_NAME = "US 130 App Schema";
    private static final String GLOBAL_SCHEMA_ID = "53bbf74d-0d9f-4a03-8f1a-b120b4fb9063";
    private static final String CONNECTOR_PATH = "/connectors/us-130-clinics-app.json";
    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";
    private static final String EXTRACTOR_DATA_PATH = "/connectors/clinic_001.csv";
    /**
     * The app the connector configuration names, read from that configuration rather than repeated
     * here: the image tag changes every time the extractor app is rebuilt, and a copy of it in the
     * test only records which tag was current the day the test was written.
     */
    private static AppBasedUploadSettingsDTO configuredApp;

    /**
     * Registers the app-based group, which ships as {@code %dev}-only because it starts a real
     * container in dev mode.
     */
    public static class AppExtractorProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "flnet.auto-subscribe." + GROUP_KEY + ".global-schema-id", GLOBAL_SCHEMA_ID,
                    "flnet.auto-subscribe." + GROUP_KEY + ".cohort-name", COHORT_NAME,
                    "flnet.auto-subscribe." + GROUP_KEY + ".connector-path", CONNECTOR_PATH,
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
    ConnectorRunStepAO stepAO;

    @Inject
    ConnectorFilesBO filesBO;

    @Inject
    PatientBO patientBO;

    @Inject
    FLNetClientConfig config;

    @Inject
    ObjectMapper objectMapper;

    private ExtractorAppStub extractorApp;

    @BeforeEach
    void installMocks() throws Exception {
        configuredApp = readConfiguredApp();

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

        extractorApp = new ExtractorAppStub();
        QuarkusMock.installMockForType(extractorApp, OrchContainerServiceClient.class, RestClient.LITERAL);
    }

    @Test
    void autoSubscribe_withAppExtractor_importsPatientsWithoutAnyFileInput() throws Exception {
        FLNetClientConfig.AutoSubscribeGroupConfig group = config.autoSubscribe().groups().get(GROUP_KEY);
        assertNotNull(group, "Test profile should contribute group '" + GROUP_KEY + "'");
        assertEquals(COHORT_NAME, group.cohortName());
        assertEquals(Optional.of(CONNECTOR_PATH), group.connectorPath());
        assertEquals(Optional.empty(), group.etlDataPath(),
                "The app ships its own data, so the group must not configure a file");

        Boolean ok = autoSubscriberBO
                .subscribeToFLNet(GROUP_KEY, group)
                .await().atMost(Duration.ofMinutes(2));
        assertTrue(Boolean.TRUE.equals(ok), "AutoSubscribe should succeed");

        // Cohort created.
        Optional<CohortEntity> cohort = cohortAO.findByName(COHORT_NAME);
        assertTrue(cohort.isPresent(), "Cohort '" + COHORT_NAME + "' should have been created");
        Long cohortId = cohort.get().getId();

        // Connector created with the app input the imported ETL configuration describes.
        List<ConnectorDTO> connectors = connectorBO.getAllByCohortId(cohortId, "test");
        assertFalse(connectors.isEmpty(), "At least one connector should exist for the cohort");
        ConnectorDTO connector = connectors.getFirst();
        AppBasedUploadSettingsDTO appSettings = assertInstanceOf(AppBasedUploadSettingsDTO.class,
                connector.getInputConfig(), "Connector input must be an app-based extractor");
        assertEquals("APP", appSettings.getMode());
        assertEquals(configuredApp.getAppImage(), appSettings.getAppImage());
        assertEquals(configuredApp.getAppVersionId(), appSettings.getAppVersionId());

        // The extractor app was started, once, with the connector's image.
        assertTrue(extractorApp.started.await(90, TimeUnit.SECONDS), "The extractor app should be started");
        assertEquals(List.of(configuredApp.getAppImage()), extractorApp.startedImages,
                "The run should start exactly the configured extractor image");

        // The run reaches FINISHED on data that only ever existed inside the app.
        ConnectorRunDTO finalRun = pollForTerminalRun(connector.getId(), 120_000);
        assertNotNull(finalRun, "A connector run should have been started and completed");
        assertEquals(ImportStatusEnum.FINISHED, finalRun.getStatus(),
                "The app-based run should finish successfully, run error: " + finalRun.getErrorMessage());

        // Patients imported, from data that only ever existed inside the app image.
        List<PatientDTO> patients = QuarkusTransaction.requiringNew()
                .call(() -> patientBO.listPatientData(cohortId));
        assertFalse(patients.isEmpty(), "Patients should have been imported into the cohort");
        assertEquals(expectedPatientCount(), patients.size(),
                "Every patient of the app's data output should be imported");

        // Every output the app uploaded is now a file of the cohort, and the connector records
        // which file each output landed in - the link later steps read instead of re-running the
        // container.
        ConnectorDTO reloaded = QuarkusTransaction.requiringNew()
                .call(() -> connectorBO.getFileCheckedById(connector.getId()));
        AppBasedUploadSettingsDTO storedSettings = assertInstanceOf(AppBasedUploadSettingsDTO.class,
                reloaded.getInputConfig(), "Connector input must still be an app-based extractor");
        Map<String, Object> outputParams = storedSettings.getOutputParams();
        assertNotNull(outputParams, "The run should have recorded where the app outputs were stored");
        // Compared as a set: the connector's input config lives in a jsonb column, and jsonb
        // orders keys itself rather than keeping insertion order.
        assertEquals(Set.of("data", "ids"), outputParams.keySet(),
                "Every declared app output should be linked, under the app's own output names");

        List<ConnectorFilesDTO> cohortFiles = QuarkusTransaction.requiringNew()
                .call(() -> filesBO.getFiles(cohortId));
        List<Long> storedFileIds = cohortFiles.stream().map(ConnectorFilesDTO::getId).toList();
        outputParams.forEach((name, fileId) -> assertTrue(
                storedFileIds.contains(Long.valueOf(String.valueOf(fileId))),
                "Output '" + name + "' should point at a file of the cohort, was " + fileId));

        // The extraction is visible under the run, like an app-based transformer step, instead of
        // being an orphaned step nothing can show.
        List<ConnectorRunStepEntity> steps = QuarkusTransaction.requiringNew()
                .call(() -> stepAO.find("connectorRun.id", finalRun.getId()).list());
        assertFalse(steps.isEmpty(), "The extractor execution should be recorded as a step of the run");
        assertEquals(RunStatusTypes.FINISHED, steps.getFirst().getStatus(),
                "The extractor step should be finished once its output was imported");
    }

    /** Reads the app input out of the connector configuration this group imports. */
    private AppBasedUploadSettingsDTO readConfiguredApp() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CONNECTOR_PATH.substring(1))) {
            assertNotNull(in, "Connector configuration " + CONNECTOR_PATH + " must be on the classpath");
            ConnectorDTO connector = objectMapper.readValue(in, ConnectorDTO.class);
            return assertInstanceOf(AppBasedUploadSettingsDTO.class, connector.getInputConfig(),
                    CONNECTOR_PATH + " must configure an app-based input");
        }
    }

    private int expectedPatientCount() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(EXTRACTOR_DATA_PATH.substring(1))) {
            assertNotNull(in, "Extractor payload " + EXTRACTOR_DATA_PATH + " must be on the classpath");
            String csv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = csv.split("\n");
            int patientColumn = List.of(lines[0].split(",")).indexOf("patient_nbr");
            return (int) java.util.Arrays.stream(lines, 1, lines.length)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(",")[patientColumn])
                    .distinct()
                    .count();
        }
    }

    private ConnectorRunDTO pollForTerminalRun(Long connectorId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        ConnectorRunDTO latest = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                // Read in a fresh transaction each time: the test method holds one request-scoped
                // Hibernate session, which would keep handing back the first, still-INIT run it
                // loaded while the ETL advances the row on its own thread.
                List<ConnectorRunDTO> runs = QuarkusTransaction.requiringNew()
                        .call(() -> connectorRunBO.getAllForConnector(connectorId));
                if (!runs.isEmpty()) {
                    latest = runs.stream().max(Comparator.comparing(ConnectorRunDTO::getId)).orElse(null);
                    if (latest != null
                            && (latest.getStatus() == ImportStatusEnum.FINISHED
                            || latest.getStatus() == ImportStatusEnum.ERROR)) {
                        return latest;
                    }
                }
            } catch (Exception ignored) {
                // transient read during import, retry
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
     * Stands in for orch-api plus docker: instead of running the extractor image it does what that
     * image would do — read its API key and step id from the container environment, then post its
     * declared outputs back to the run's upload endpoint.
     */
    private static class ExtractorAppStub implements OrchContainerServiceClient {

        private static final String IDS_MAPPING_CSV = """
                admission_type_id,description
                1,Emergency
                2,Urgent
                3,Elective
                """;

        final CountDownLatch started = new CountDownLatch(1);
        final List<String> startedImages = new CopyOnWriteArrayList<>();

        @Override
        public CreateContainerResponseDTO startContainer(StartAppDTO createDTO,
                                                         boolean changeUrl,
                                                         boolean sendConsoleLog,
                                                         String path,
                                                         Integer port) {
            startedImages.add(createDTO.getAppImage());
            Long stepId = environmentValue(createDTO, "APP_ID").map(Long::valueOf).orElseThrow(
                    () -> new IllegalStateException("Container environment is missing APP_ID"));
            String apiKey = environmentValue(createDTO, "APP_API_KEY").orElseThrow(
                    () -> new IllegalStateException("Container environment is missing APP_API_KEY"));

            Thread appThread = new Thread(() -> uploadOutputs(stepId, apiKey), "extractor-app-stub");
            appThread.setDaemon(true);
            appThread.start();

            started.countDown();
            CreateContainerResponseDTO response = new CreateContainerResponseDTO();
            response.setId("extractor-app-stub-" + stepId);
            return response;
        }

        private void uploadOutputs(Long stepId, String apiKey) {
            try {
                Path data = writeTempCopy("data", classpathBytes(EXTRACTOR_DATA_PATH));
                Path ids = writeTempCopy("ids", IDS_MAPPING_CSV.getBytes(StandardCharsets.UTF_8));

                RestAssured.given()
                        .header(ToolApiKeyService.HEADER_NAME, apiKey)
                        .multiPart(new MultiPartSpecBuilder(data.toFile())
                                .controlName("files")
                                .fileName("data.csv")
                                .mimeType("application/octet-stream")
                                .header("X-Key", "data")
                                .header("X-Name", "data")
                                .build())
                        .multiPart(new MultiPartSpecBuilder(ids.toFile())
                                .controlName("files")
                                .fileName("ids.csv")
                                .mimeType("application/octet-stream")
                                .header("X-Key", "ids")
                                .header("X-Name", "ids")
                                .build())
                        .multiPart("runId", String.valueOf(stepId))
                        .when()
                        .post("/connectors/run/execution/{runId}/upload/output", stepId)
                        .then()
                        .statusCode(201);
            } catch (Exception e) {
                throw new IllegalStateException("Extractor app stub failed to upload its output", e);
            }
        }

        private static byte[] classpathBytes(String resource) throws Exception {
            try (InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resource.substring(1))) {
                if (in == null) {
                    throw new IllegalStateException("Missing classpath resource " + resource);
                }
                return in.readAllBytes();
            }
        }

        private static Path writeTempCopy(String name, byte[] content) throws Exception {
            Path file = Files.createTempFile("extractor-app-stub-" + name + "-", ".csv");
            Files.write(file, content);
            file.toFile().deleteOnExit();
            return file;
        }

        private static Optional<String> environmentValue(StartAppDTO dto, String key) {
            List<String> environments = dto.getEnvironments();
            if (environments == null) {
                return Optional.empty();
            }
            return environments.stream()
                    .filter(entry -> entry != null && entry.startsWith(key + "="))
                    .map(entry -> entry.substring(key.length() + 1))
                    .findFirst();
        }

        @Override
        public Response cleanupWorkflow(String containerId, boolean cleanup) {
            return Response.ok().build();
        }

        @Override
        public List<ContainerDTO> list() {
            return List.of();
        }

        @Override
        public ContainerDTO get(String id) {
            return null;
        }

        @Override
        public Multi<String> getLogsStream(String id) {
            return Multi.createFrom().empty();
        }

        @Override
        public List<ContainerRunDTO> listRuns() {
            return List.of();
        }

        @Override
        public ContainerRunDTO getRun(Long id) {
            return null;
        }

        @Override
        public List<ContainerLogDTO> getRunLogs(Long id) {
            return List.of();
        }

        @Override
        public CreateContainerResponseDTO startPipeline(StartPipelineDTO createDTO,
                                                        boolean changeUrl,
                                                        String path,
                                                        Integer port) {
            throw new UnsupportedOperationException("not used by the extractor path");
        }

        @Override
        public List<ContainerDTO> listFeatureCloud() {
            return List.of();
        }

        @Override
        public Response cleanupContainers(List<String> containerIds, boolean cleanup) {
            return Response.ok().build();
        }
    }
}
