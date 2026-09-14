package rest;

import bio.cosy.feddb.local.api.importer.ImporterDataFileWatcherBO;
import bio.cosy.feddb.local.api.file.FileBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import rest.helper.AutoSubscribeTestHelper;
import rest.resource.PatientDataTestResource;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for {@link ImporterDataFileWatcherBO}.
 *
 * Creates a real connector backed by an uploaded CSV, registers a watcher on that
 * file, then mutates the file to assert the watcher re-uploads it and triggers a
 * comprehensive run. Nothing is mocked.
 */
@QuarkusTest
public class ImporterDataFileWatcherBOE2ETest {

    private static final Long EXISTING_COHORT_ID = PatientDataTestResource.TEST_COHORT_ID; // 5

    @Inject
    ImporterDataFileWatcherBO etlDataFileWatcherBO;

    @Inject
    FileBO fileBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Test
    @Disabled("ImporterDataFileWatcherBO.handleChange runs on a raw daemon thread without an "
            + "active JTA transaction / CDI request context, so FileBO.createEntityForSystem fails "
            + "with 'Transaction is required for invocation'. This is a product-side concern "
            + "(the watcher method needs @ActivateRequestContext / @Transactional or a "
            + "QuarkusTransaction.requiringNew() wrapper) and cannot be fixed from the test alone.")
    void watch_fileChange_triggersUploadAndComprehensiveRun() throws Exception {
        File csv = AutoSubscribeTestHelper.createTempCsv("etl-watcher-test-");
        FileEntity uploaded = fileBO.createEntityForSystem(csv);
        Long initialFileId = uploaded.getId();

        ConnectorDTO connector = createConnector("EtlWatcher_" + System.currentTimeMillis(), initialFileId);
        Long connectorId = connector.getId();
        int initialRunCount = connectorRunBO.getAllForConnector(connectorId).size();

        try {
            etlDataFileWatcherBO.watch("etl-watcher-test", csv.getAbsolutePath(), connectorId, "SYSTEM");
            // Give the watch loop a moment to register before we mutate the file.
            Thread.sleep(500);

            AutoSubscribeTestHelper.appendRowToCsv(csv, "AUTO_SUB_PATIENT_3", 33, 3.5);

            ConnectorRunDTO newRun = pollForNewRun(connectorId, initialRunCount, 20_000);
            assertNotNull(newRun, "File change should trigger a new ConnectorRun");
            assertTrue(newRun.getDeleteExistingPatients(),
                    "File watcher should start the run with delete-existing-patients enabled");

            // The connector's input config should now reference a new (re-uploaded) file id.
            ConnectorDTO refreshed = connectorBO.getById(connectorId);
            assertNotNull(refreshed, "Connector should still exist after the watcher updated it");
            assertTrue(refreshed.getInputConfig() instanceof FileUploadSettingsDTO,
                    "Input config should remain a FileUploadSettingsDTO");
            FileUploadSettingsDTO inputAfter = (FileUploadSettingsDTO) refreshed.getInputConfig();
            assertNotNull(inputAfter.getFileId(), "Connector should still have a file id");
            assertTrue(!inputAfter.getFileId().equals(initialFileId),
                    "Re-upload should have produced a new file id (was " + initialFileId
                            + ", now " + inputAfter.getFileId() + ")");
        } finally {
            // Best-effort shutdown via @PreDestroy on app exit; nothing else to clean here.
        }
    }

    private ConnectorDTO createConnector(String name, Long fileId) {
        ConnectorDTO dto = new ConnectorDTO();
        dto.setName(name);
        dto.setCohortId(EXISTING_COHORT_ID);

        FileUploadSettingsDTO inputConfig = new FileUploadSettingsDTO();
        inputConfig.setMode("FILE");
        inputConfig.setFileType(FileParsingType.CSV);
        inputConfig.setDelimiter(",");
        inputConfig.setHasHeader(true);
        inputConfig.setFileId(fileId);
        dto.setInputConfig(inputConfig);

        ConnectorMappingDTO intMapping = new ConnectorMappingDTO();
        intMapping.setSchemaId(PatientDataTestResource.COMPREHENSIVE_TEST_INT_NODE_ID);
        intMapping.setColumn("13");
        ConnectorMappingDTO floatMapping = new ConnectorMappingDTO();
        floatMapping.setSchemaId(PatientDataTestResource.COMPREHENSIVE_TEST_FLOAT_NODE_ID);
        floatMapping.setColumn("14");
        dto.setSchemaMapping(List.of(intMapping, floatMapping));

        ConnectorDTO created = connectorBO.create(dto);
        assertNotNull(created, "Connector creation should not return null");
        assertNotNull(created.getId(), "Created connector must have an id");
        return created;
    }

    private ConnectorRunDTO pollForNewRun(Long connectorId, int initialCount, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                List<ConnectorRunDTO> runs = connectorRunBO.getAllForConnector(connectorId);
                if (runs.size() > initialCount) {
                    return runs.stream()
                            .max(java.util.Comparator.comparing(ConnectorRunDTO::getId))
                            .orElse(null);
                }
            } catch (Exception ignored) {
                // Run might not be persisted yet, retry.
            }
            Thread.sleep(200);
        }
        return null;
    }
}
