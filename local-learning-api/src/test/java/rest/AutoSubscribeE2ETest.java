package rest;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.datamodler.GlobalSchemaSubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@QuarkusTest
public class AutoSubscribeE2ETest {

    private static final String GROUP_KEY = "us-130";
    private static final String EXPECTED_SCHEMA_NAME = "US 130 Schema";
    private static final String EXPECTED_GLOBAL_SCHEMA_ID = "5e9bac19-0633-4121-a4fe-04d75977dcb7";
    private static final String CONNECTOR_PATH = "/connectors/us-130-clinics.json";
    private static final String ETL_DATA_PATH = "/connectors/clinic_001.csv";
    private static final String SCHEMA_DUMMY_PATH = "/connectors/global_schema_dummy.json";

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

    @BeforeEach
    void installGlobalSchemaMock() throws Exception {
        SchemaStructureDTO dummySchema;
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SCHEMA_DUMMY_PATH.substring(1))) {
            assertNotNull(in, "Test dummy schema " + SCHEMA_DUMMY_PATH + " must be on the classpath");
            dummySchema = objectMapper.readValue(in, SchemaStructureDTO.class);
        }

        GlobalSchemaSubscriptionService mock = new GlobalSchemaSubscriptionService() {
            @Override
            public Uni<SchemaStructureDTO> subscribe(UUID id) {
                return Uni.createFrom().item(dummySchema);
            }

            @Override
            public Uni<Response> unsubscribe(UUID id) {
                return Uni.createFrom().item(Response.ok().build());
            }
        };

        QuarkusMock.installMockForType(mock, GlobalSchemaSubscriptionService.class, RestClient.LITERAL);
    }

    @Test
    void autoSubscribe_fullFlow_createsCohortConnectorFileRunAndPatients() {
        // 2. Assert the %test config is present and correct.
        FLNetClientConfig.AutoSubscribeGroupConfig group = config.autoSubscribe().groups().get(GROUP_KEY);
        assertNotNull(group, "Test config should contain group '" + GROUP_KEY + "'");
        assertEquals(EXPECTED_GLOBAL_SCHEMA_ID, group.globalSchemaId());
        assertEquals(EXPECTED_SCHEMA_NAME, group.cohortName());
        assertEquals(Optional.of(CONNECTOR_PATH), group.connectorPath());
        assertEquals(Optional.of(ETL_DATA_PATH), group.etlDataPath());
        assertEquals(3, group.defaultCohortPermission().queryRetryTime());
        assertEquals(Boolean.TRUE, group.defaultCohortPermission().isAllowedToQuery());
        assertEquals(100, group.defaultCohortPermission().querySampleThreshold());
        assertEquals(Boolean.FALSE, group.fileWatching());

        // Run the auto-subscribe flow synchronously.
        Boolean ok = autoSubscriberBO
                .subscribeToFLNet(config.autoSubscribe().groups())
                .await().atMost(Duration.ofSeconds(60));
        assertTrue(Boolean.TRUE.equals(ok), "AutoSubscribe should succeed");

        // 1. Cohort created.
        Optional<CohortEntity> cohort = cohortAO.findByName(EXPECTED_SCHEMA_NAME);
        assertTrue(cohort.isPresent(), "Cohort '" + EXPECTED_SCHEMA_NAME + "' should have been created");
        Long cohortId = cohort.get().getId();

        // 3. Connector created for the cohort.
        List<ConnectorDTO> connectors = connectorBO.getAllByCohortId(cohortId, "SYSTEM");
        assertFalse(connectors.isEmpty(), "At least one connector should exist for the cohort");
        ConnectorDTO connector = connectors.get(0);
        assertNotNull(connector.getId());

        // 4. File uploaded and attached to the connector.
        assertInstanceOf(FileUploadSettingsDTO.class, connector.getInputConfig(),
                "Connector input must be a file upload settings DTO");
        FileUploadSettingsDTO fileSettings = (FileUploadSettingsDTO) connector.getInputConfig();
        assertNotNull(fileSettings.getFileId(), "Connector should have an uploaded file id attached");
        assertTrue(fileSettings.isFileExists(),
                "Uploaded file should actually exist in the local file store");

        // 5. Connector run reaches a terminal state.
        ConnectorRunDTO finalRun = pollForTerminalRun(connector.getId(), 60_000);
        assertNotNull(finalRun, "A connector run should have been started and completed");
        assertEquals(true, finalRun.getDeleteExistingPatients(),
                "Run should delete existing patients before import");
        assertEquals(ImportStatusEnum.FINISHED, finalRun.getStatus(),
                "The comprehensive run should finish successfully");

        // 6. Patients created in the cohort.
        List<PatientDTO> patients = patientBO.listPatientData(cohortId);
        assertFalse(patients.isEmpty(), "Patients should have been imported into the cohort");
    }

    private ConnectorRunDTO pollForTerminalRun(Long connectorId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        ConnectorRunDTO latest = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                List<ConnectorRunDTO> runs = connectorRunBO.getAllForConnector(connectorId);
                if (!runs.isEmpty()) {
                    latest = runs.stream()
                            .max(java.util.Comparator.comparing(ConnectorRunDTO::getId))
                            .orElse(null);
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
}
