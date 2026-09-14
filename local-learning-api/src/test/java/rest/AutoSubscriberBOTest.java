package rest;

import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.file.FileBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.ImportStatusEnum;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import rest.helper.AutoSubscribeTestHelper;

import java.io.File;
import java.io.FileWriter;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for {@link AutoSubscriberBO}.
 * <p>
 * Uses the pre-existing test cohort COHORT_TEST (id=5) so the test exercises the
 * "cohort already exists" branch and does not depend on the remote global schema service.
 */
@QuarkusTest
public class AutoSubscriberBOTest {

    private static final String EXISTING_COHORT_NAME = "COHORT_004";
    private static final Long EXISTING_COHORT_ID = 4L;
    private static final Long EXISTING_COHORT_SCHEMA_NODE_ID = 10L;

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    FileBO fileBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Test
    void subscribeToFLNet_existingCohort_existingConnector_runsConnector() throws Exception {
        // Sanity: the cohort must exist in the test SQL.
        assertTrue(QuarkusTransaction.requiringNew().call(
                        () -> cohortAO.findByName(EXISTING_COHORT_NAME).isPresent()),
                "Test SQL should pre-create cohort " + EXISTING_COHORT_NAME);

        List<ConnectorDTO> existingConnectors = ensureExistingConnectors("AutoSub_Existing_");
        Map<Long, Integer> initialRunCounts = snapshotRunCounts(existingConnectors);
        Set<Long> existingConnectorIds = connectorIds(existingConnectors);

        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig = AutoSubscribeTestHelper.buildGroupConfig(
                EXISTING_COHORT_NAME,
                Optional.of("/this/path/should/be/ignored.json"),
                Optional.empty(),
                false);

        Boolean ok = autoSubscriberBO
                .subscribeToFLNet("auto-sub-existing", groupConfig)
                .await().atMost(Duration.ofSeconds(25));

        assertTrue(Boolean.TRUE.equals(ok),
                "AutoSubscribe should succeed for an existing cohort + existing connector");

        Set<Long> connectorIdsAfter = QuarkusTransaction.requiringNew().call(() ->
                connectorIds(connectorBO.getAllByCohortId(EXISTING_COHORT_ID, "SYSTEM")));
        assertEquals(existingConnectorIds, connectorIdsAfter,
                "AutoSubscribe should reuse the cohort's existing connectors without creating duplicates");

        ConnectorRunDTO newRun = pollForNewRun(initialRunCounts, 25_000);
        assertNotNull(newRun, "A new run should have been started for one of the existing connectors");
        assertTrue(existingConnectorIds.contains(newRun.getConnectorId()),
                "The new run should belong to one of the cohort's pre-existing connectors");
        assertTerminalStatus(newRun.getId(), 25_000);
    }

    @Test
    void subscribeToFLNet_groupsMap_aggregatesResults() throws Exception {
        List<ConnectorDTO> existingConnectors = ensureExistingConnectors("AutoSub_Map_");
        Map<Long, Integer> initialRunCounts = snapshotRunCounts(existingConnectors);

        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig = AutoSubscribeTestHelper.buildGroupConfig(
                EXISTING_COHORT_NAME,
                Optional.of("/ignored.json"),
                Optional.empty(),
                false);

        Uni<Boolean> result = autoSubscriberBO.subscribeToFLNet(Map.of("g1", groupConfig));
        Boolean ok = result.await().atMost(Duration.ofSeconds(15));

        assertTrue(Boolean.TRUE.equals(ok), "Map variant should succeed and aggregate results");
        ConnectorRunDTO newRun = pollForNewRun(initialRunCounts, 15_000);
        assertNotNull(newRun,
                "A new run should have been started via the map variant");
        assertTerminalStatus(newRun.getId(), 15_000);
    }

    @Test
    void importFromRemoteUrl_thenAttachFile_preservesTransformers() throws Exception {
        File csv = createHeaderOnlyCsv("auto-sub-import-");
        FileEntity uploaded = QuarkusTransaction.requiringNew().call(() -> fileBO.createEntityForSystem(csv));

        ConnectorDTO imported = QuarkusTransaction.requiringNew().call(() ->
                connectorBO.importFromRemoteUrl("/connectors/auto-subscriber-import-test.json", EXISTING_COHORT_ID, "SYSTEM", null));

        assertNotNull(imported, "Imported connector should not be null");
        assertNotNull(imported.getId(), "Imported connector should have an id");
        assertNotNull(imported.getTransformer(), "Imported connector should include transformers");
        assertEquals(1, imported.getTransformer().size(),
                "Imported connector should expose the transformer created from the resource payload");

        try {
            assertInstanceOf(FileUploadSettingsDTO.class, imported.getInputConfig(),
                    "Imported connector should use file upload settings");
            FileUploadSettingsDTO fileSettings = (FileUploadSettingsDTO) imported.getInputConfig();
            fileSettings.setFileId(uploaded.getId());

            ConnectorDTO updated = QuarkusTransaction.requiringNew().call(() ->
                    connectorBO.update(imported.getId(), imported, "SYSTEM"));

            assertNotNull(updated.getTransformer(), "Updated connector should still include transformers");
            assertEquals(1, updated.getTransformer().size(),
                    "Attaching a file must not remove imported transformers");

            ConnectorDTO reloaded = QuarkusTransaction.requiringNew().call(() ->
                    connectorBO.getById(imported.getId()));

            assertNotNull(reloaded.getTransformer(), "Reloaded connector should still include transformers");
            assertEquals(1, reloaded.getTransformer().size(),
                    "Persisted connector should keep the imported transformer after update");
        } finally {
            QuarkusTransaction.requiringNew().run(() -> connectorBO.delete(imported.getId()));
        }
    }

    // ---------------- helpers ----------------

    private ConnectorDTO createConnector(String connectorName) {
        File csv;
        try {
            csv = createHeaderOnlyCsv("auto-sub-test-");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        FileEntity uploaded = fileBO.createEntityForSystem(csv);

        ConnectorDTO dto = new ConnectorDTO();
        dto.setName(connectorName);
        dto.setCohortId(EXISTING_COHORT_ID);

        FileUploadSettingsDTO inputConfig = new FileUploadSettingsDTO();
        inputConfig.setMode("FILE");
        inputConfig.setFileType(FileParsingType.CSV);
        inputConfig.setDelimiter(",");
        inputConfig.setHasHeader(true);
        inputConfig.setFileId(uploaded.getId());
        dto.setInputConfig(inputConfig);

        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setSchemaId(EXISTING_COHORT_SCHEMA_NODE_ID);
        mapping.setColumn("13");
        dto.setSchemaMapping(List.of(mapping));

        ConnectorDTO created = connectorBO.create(dto);
        assertNotNull(created, "Connector creation should not return null");
        assertNotNull(created.getId(), "Created connector must have an id");
        return created;
    }

    private File createHeaderOnlyCsv(String prefix) throws Exception {
        File csv = java.nio.file.Files.createTempFile(prefix, ".csv").toFile();
        try (FileWriter w = new FileWriter(csv)) {
            w.write("Unique Patient ID,13\n");
        }
        csv.deleteOnExit();
        return csv;
    }

    private List<ConnectorDTO> ensureExistingConnectors(String connectorNamePrefix) {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<ConnectorDTO> connectors = connectorBO.getAllByCohortId(EXISTING_COHORT_ID, "SYSTEM");
            if (connectors.isEmpty()) {
                return List.of(createConnector(connectorNamePrefix + System.currentTimeMillis()));
            }
            return connectors;
        });
    }

    private Map<Long, Integer> snapshotRunCounts(List<ConnectorDTO> connectors) {
        return QuarkusTransaction.requiringNew().call(() ->
                connectors.stream().collect(Collectors.toMap(
                        ConnectorDTO::getId,
                        connector -> connectorRunBO.getAllForConnector(connector.getId()).size()
                )));
    }

    private Set<Long> connectorIds(List<ConnectorDTO> connectors) {
        return connectors.stream()
                .map(ConnectorDTO::getId)
                .collect(Collectors.toSet());
    }

    private ConnectorRunDTO pollForNewRun(Map<Long, Integer> initialRunCounts, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                ConnectorRunDTO latest = QuarkusTransaction.requiringNew().call(() -> {
                    return initialRunCounts.entrySet().stream()
                            .map(entry -> newestRunIfCountIncreased(entry.getKey(), entry.getValue()))
                            .filter(run -> run != null)
                            .max(Comparator.comparing(ConnectorRunDTO::getId))
                            .orElse(null);
                });
                if (latest != null) {
                    return latest;
                }
            } catch (Exception ignored) {
                // Run might not be persisted yet, retry.
            }
            Thread.sleep(200);
        }
        return null;
    }

    private ConnectorRunDTO newestRunIfCountIncreased(Long connectorId, int initialCount) {
        List<ConnectorRunDTO> runs = connectorRunBO.getAllForConnector(connectorId);
        if (runs.size() <= initialCount) {
            return null;
        }
        return runs.stream()
                .max(Comparator.comparing(ConnectorRunDTO::getId))
                .orElse(null);
    }

    private void assertTerminalStatus(Long runId, long timeoutMillis) throws InterruptedException {
        ConnectorRunDTO finishedRun = pollForTerminalRun(runId, timeoutMillis);
        assertNotNull(finishedRun, "Connector run " + runId + " should reach a terminal status");
        assertTrue(
                finishedRun.getStatus() == ImportStatusEnum.FINISHED || finishedRun.getStatus() == ImportStatusEnum.ERROR,
                "Connector run should finish or error before test shutdown, was " + finishedRun.getStatus()
        );
    }

    private ConnectorRunDTO pollForTerminalRun(Long runId, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                ConnectorRunDTO run = QuarkusTransaction.requiringNew().call(() ->
                        connectorRunBO.getById(runId));
                if (run != null && (run.getStatus() == ImportStatusEnum.FINISHED || run.getStatus() == ImportStatusEnum.ERROR)) {
                    return run;
                }
            } catch (Exception ignored) {
                // Run might still be in-flight, retry.
            }
            Thread.sleep(200);
        }
        return null;
    }
}
