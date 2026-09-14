package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.tools.log.PatientToolRunLogAO;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import bio.cosy.feddb.local.services.orch.OrchVolumeServiceClient;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class PatientToolRunBOTest {

    private static final Long COHORT_ID = 1L;
    private static final Long APP_VERSION_ID = 7L;

    @Inject
    PatientToolRunBO toolRunBO;

    @Inject
    PatientToolRunAO runAO;

    @Inject
    PatientToolRunFileAO fileAO;

    @Inject
    PatientToolRunLogAO logAO;

    @Inject
    CohortAO cohortAO;

    @Inject
    CohortBO cohortBO;

    @Inject
    EntityManager entityManager;

    @InjectMock
    WorkflowOrchestratorBO workflowOrchestrator;

    @InjectMock
    @RestClient
    OrchVolumeServiceClient volumeClient;

    @InjectMock
    @RestClient
    GlobalAPIStoreService globalAPIStoreService;

    @BeforeEach
    void setUp() {
        ToolInputConfigDTO input = new ToolInputConfigDTO();
        input.setName("input");
        input.setVariableName("input");
        ToolConfigsDTO config = new ToolConfigsDTO();
        config.getInput().add(input);

        FederatedAppDetailDTO app = new FederatedAppDetailDTO();
        app.setName("Patient UMAP Export");
        app.setImageName("registry.example/patient-umap-export:1");
        app.setAppConfig(config);

        when(globalAPIStoreService.getAppByVersion(anyLong())).thenReturn(app);
        when(volumeClient.uploadFilesIds(anyLong(), anyLong(), any())).thenAnswer(i -> Response.ok().build());
        when(workflowOrchestrator.executeWorkflow(any(), anyString())).thenReturn("container-1");
    }

    @Test
    void startStagesTheInputBeforeTheContainerStarts() throws IOException {
        Path input = exportFile();

        PatientToolRunDTO run = toolRunBO.start(COHORT_ID, APP_VERSION_ID, new LinkedHashMap<>(), PatientToolRunType.EXPORT, input);
        assertEquals(RunStatusTypes.PENDING, run.getRunStatus());

        PatientToolRunDTO started = awaitStatus(run.getId(), RunStatusTypes.STARTED);
        assertEquals("container-1", started.getContainerId());
        assertFalse(Files.exists(input), "the staged input file is removed");

        ArgumentCaptor<StartWorkflowNodeDTO> start = ArgumentCaptor.forClass(StartWorkflowNodeDTO.class);
        InOrder order = inOrder(volumeClient, workflowOrchestrator);
        order.verify(volumeClient).uploadFilesIds(eq(-2L), eq(run.getId()), any());
        order.verify(workflowOrchestrator).executeWorkflow(start.capture(), eq("patient/tool/run"));

        StartWorkflowNodeDTO dto = start.getValue();
        assertEquals(-2L, dto.getWorkflowId());
        assertEquals(run.getId(), dto.getWorkflowNodeId());
        assertTrue(dto.getIsFistNode());
        assertFalse(dto.getEnableRemoteResultSaving());
        assertTrue(dto.getEnvironments().contains("APP_ID=" + run.getId()));
        assertTrue(dto.getEnvironments().stream().anyMatch(env -> env.startsWith("APP_API_KEY=")));

        // the platform's steps are logged, so the progress stream shows them next to the app's messages
        List<String> steps = toolRunBO.snapshot(run.getId(), 0L).getLogs()
                .stream().map(RunMessageLogDTO::getMessage).toList();
        assertTrue(steps.stream().anyMatch(step -> step.startsWith("Handing the patient data to the app")), steps.toString());
        assertTrue(steps.stream().anyMatch(step -> step.startsWith("Container started")), steps.toString());
    }

    @Test
    void finishedRunHandsOutItsOutputsAndRemovesTheContainer() throws IOException {
        Long runId = startedRun();
        Path volume = Files.createTempDirectory("output-volume");
        when(workflowOrchestrator.getFiles(-2L, runId)).thenReturn(List.of(
                Files.writeString(volume.resolve("plot.html"), "<html></html>").toFile(),
                Files.writeString(volume.resolve("embedding.csv"), "patientId,umap_1,umap_2\n").toFile()));

        toolRunBO.completeRun(runId);

        assertEquals(RunStatusTypes.FINISHED, find(runId).getRunStatus());
        verify(workflowOrchestrator).cleanup("container-1", true);

        List<PatientToolRunProgressDTO> events = toolRunBO.progress(runId)
                .collect().asList().await().atMost(Duration.ofSeconds(10));
        PatientToolRunProgressDTO last = events.getLast();
        assertEquals(RunStatusTypes.FINISHED, last.getStatus().getRunStatus());
        assertTrue(last.isDownloadReady());
        assertEquals(List.of("plot", "embedding"), last.getOutputs().stream().map(PatientToolRunOutputDTO::getKey).toList());
        assertEquals(List.of(ToolConfigDataType.HTML, ToolConfigDataType.CSV),
                last.getOutputs().stream().map(PatientToolRunOutputDTO::getType).toList());

        assertThrows(NotFoundException.class, () -> loadOutputs(COHORT_ID + 1, runId));
        FileResult outputs = loadOutputs(COHORT_ID, runId);
        assertEquals("cohort_" + COHORT_ID + "_export_" + runId + ".zip", outputs.fileName());
        assertEquals(List.of("plot.html", "embedding.csv"), zipEntries(Files.readAllBytes(outputs.file().toPath())));
        // the zip is stored with the run, so it can be downloaded again
        assertEquals(List.of("plot.html", "embedding.csv"),
                zipEntries(Files.readAllBytes(loadOutputs(COHORT_ID, runId).file().toPath())));
        assertEquals(COHORT_ID, find(runId).getCohortId());

        PatientToolRunSummaryDTO listed = QuarkusTransaction.requiringNew()
                .call(() -> toolRunBO.listRuns(COHORT_ID, PatientToolRunType.EXPORT)).stream()
                .filter(summary -> summary.getId().equals(runId))
                .findFirst().orElseThrow();
        assertEquals("Patient UMAP Export", listed.getAppName());
        assertEquals(RunStatusTypes.FINISHED, listed.getRunStatus());
        assertTrue(listed.isDownloadReady());
        assertEquals(outputs.fileName(), listed.getOutputFileName());
        assertTrue(last.getLogs().stream().anyMatch(log -> log.getMessage().startsWith("Stored 2 outputs")));
    }

    private FileResult loadOutputs(Long cohortId, Long runId) {
        return QuarkusTransaction.requiringNew().call(() -> toolRunBO.loadOutputs(cohortId, runId));
    }

    private static List<String> zipEntries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    @Test
    void runThatCannotStartEndsTheProgressStreamWithTheError() throws IOException {
        when(workflowOrchestrator.executeWorkflow(any(), anyString())).thenThrow(new IllegalStateException("orchestrator down"));

        Long runId = toolRunBO.start(COHORT_ID, APP_VERSION_ID, new LinkedHashMap<>(), PatientToolRunType.EXPORT, exportFile()).getId();

        List<PatientToolRunProgressDTO> events = toolRunBO.progress(runId)
                .collect().asList().await().atMost(Duration.ofSeconds(10));
        PatientToolRunProgressDTO last = events.getLast();
        assertEquals(RunStatusTypes.ERROR, last.getStatus().getRunStatus());
        assertTrue(last.getStatus().getLastError().contains("orchestrator down"));
        assertFalse(last.isDownloadReady());
    }

    @Test
    void progressStreamDeliversLogLinesAndStatusAsTheyHappen() throws IOException {
        Long runId = startedRun();
        AssertSubscriber<PatientToolRunProgressDTO> events = toolRunBO.progress(runId)
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        events.awaitItems(1, Duration.ofSeconds(5));

        toolRunBO.logStep(runId, "A live step");
        toolRunBO.fail(runId, "Stopped by the test");

        // Well below the heartbeat, so both have to come from events rather than a re-read
        events.awaitCompletion(Duration.ofSeconds(5));
        List<String> messages = events.getItems().stream()
                .flatMap(event -> event.getLogs().stream())
                .map(RunMessageLogDTO::getMessage)
                .toList();
        assertTrue(messages.contains("A live step"), messages.toString());
        assertEquals(1, messages.stream().filter("A live step"::equals).count(), "each line is sent once");
        PatientToolRunProgressDTO last = events.getItems().getLast();
        assertEquals(RunStatusTypes.ERROR, last.getStatus().getRunStatus());
        assertEquals("Stopped by the test", last.getStatus().getLastError());
    }

    @Test
    void deletingTheCohortRemovesItsRunsWithTheirOutputsAndLogs() throws IOException {
        Long cohortId = QuarkusTransaction.requiringNew().call(() -> {
            CohortEntity cohort = new CohortEntity();
            cohort.setName("patient-tool-run-deletion-" + System.nanoTime());
            cohort.setKeycloakId("test");
            cohortAO.persist(cohort);
            return cohort.getId();
        });
        Long runId = toolRunBO.start(cohortId, APP_VERSION_ID, new LinkedHashMap<>(), PatientToolRunType.EXPORT, exportFile()).getId();
        awaitStatus(runId, RunStatusTypes.STARTED);
        Path volume = Files.createTempDirectory("output-volume");
        when(workflowOrchestrator.getFiles(-2L, runId)).thenReturn(List.of(
                Files.writeString(volume.resolve("plot.html"), "<html></html>").toFile()));
        toolRunBO.completeRun(runId);
        Long largeObjectId = QuarkusTransaction.requiringNew()
                .call(() -> fileAO.findByRunId(runId).orElseThrow().getLargeObjectId());
        assertTrue(QuarkusTransaction.requiringNew().call(() -> logAO.count("run.id", runId)) > 0);

        cohortBO.executeDeletion(cohortId);

        QuarkusTransaction.requiringNew().run(() -> {
            assertNull(cohortAO.findById(cohortId));
            assertTrue(runAO.findByIdOptional(runId).isEmpty());
            assertTrue(fileAO.findByRunId(runId).isEmpty());
            assertEquals(0L, logAO.count("run.id", runId));
            Number largeObjects = (Number) entityManager
                    .createNativeQuery("select count(*) from pg_largeobject_metadata where oid::bigint = :oid")
                    .setParameter("oid", largeObjectId)
                    .getSingleResult();
            assertEquals(0, largeObjects.intValue(), "the stored zip's large object is unlinked");
        });
    }

    private Long startedRun() throws IOException {
        Long runId = toolRunBO.start(COHORT_ID, APP_VERSION_ID, new LinkedHashMap<>(), PatientToolRunType.EXPORT, exportFile()).getId();
        awaitStatus(runId, RunStatusTypes.STARTED);
        return runId;
    }

    private static Path exportFile() throws IOException {
        Path input = Files.createTempFile("patient-export", ".csv");
        Files.writeString(input, "patientId,name,value\n1,age,40\n");
        return input;
    }

    private PatientToolRunDTO find(Long runId) {
        return QuarkusTransaction.requiringNew().call(() -> toolRunBO.getById(runId));
    }

    private PatientToolRunDTO awaitStatus(Long runId, RunStatusTypes status) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        PatientToolRunDTO run = find(runId);
        while (run.getRunStatus() != status && System.nanoTime() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            run = find(runId);
        }
        assertEquals(status, run.getRunStatus());
        return run;
    }
}
