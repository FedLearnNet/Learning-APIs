package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.helper.FileHelper;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.file.ExecutionFileBO;
import bio.cosy.feddb.local.execution.run.*;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class PatientToolBO {

    public static final String DATA_NAME = "data";
    private static final int MAX_HISTORY = 50;
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(30);

    @Inject
    @RestClient
    GlobalAPIStoreService globalAPIStoreService;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionRunBO runBO;

    @Inject
    ExecutionRunMapper mapper;

    @Inject
    ExecutionFileBO executionFileBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    FLNetClientConfig config;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // Input of a run until its app's volume holds it.
    private final Map<Long, Path> inputs = new ConcurrentHashMap<>();

    /** Starts the app on the input file; the file is removed once the app has it. */
    @Transactional
    public ExecutionRunDTO startTransactional(Long cohortId, Long appVersionId, LinkedHashMap<String, Object> hyperParams,
                                 PatientToolRunType toolType, Path input) {
        if (appVersionId == null) {
            throw new BadRequestException("No app version selected");
        }
        FederatedAppDetailDTO app = globalAPIStoreService.getAppByVersion(appVersionId);
        if (app == null || StringUtils.isEmpty(app.getImageName())) {
            throw new NotFoundException("No image found for app version id: " + appVersionId);
        }
        if (StringUtils.isEmpty(app.getFirstInputVariableName())) {
            throw new BadRequestException("App '" + app.getName() + "' declares no input to receive the patient data");
        }
        LinkedHashMap<String, String> inputFilePaths = new LinkedHashMap<>();
        inputFilePaths.put(app.getFirstInputVariableName(), DATA_NAME);

        ExecutionRunEntity run = workflowExecutionBO.executeAppTransactional(app.getName(), hyperParams, step -> {
            step.setGlobalAppVersionId(appVersionId);
            step.setInputFilePaths(inputFilePaths);
        });
        run.setCohort(cohortId == null ? null : cohortAO.findByIdOptional(cohortId)
                .orElseThrow(() -> new NotFoundException("Cohort " + cohortId + " not found")));
        run.setToolType(toolType);
        run.setKeycloakId(config.user().systemUserName());
        inputs.put(run.getId(), input);
        Log.infof("Started patient tool run %d (%s) of %s", run.getId(), toolType, app.getImageName());
        return mapper.mapRunEntityToDTO(run, false);
    }

    /**
     * Runs the app and hands over what it wrote, as files of their own names, once its run ended; no thread waits
     * meanwhile. A run taking longer than the timeout is stopped.
     */
    public Uni<List<Path>> run(Long cohortId, Long appVersionId, LinkedHashMap<String, Object> hyperParams,
                               PatientToolRunType toolType, Path input) {
        return Uni.createFrom().item(() -> startTransactional(cohortId, appVersionId, hyperParams, toolType, input).getId())
                .runSubscriptionOn(virtualThreads)
                .chain(runId -> runBO.awaitEnd(runId, RUN_TIMEOUT)
                        .emitOn(virtualThreads)
                        .map(last -> handOverOutputs(runId, last)));
    }

    private List<Path> handOverOutputs(Long runId, ExecutionRunDTO last) {
        if (last == null || last.getStatus() != RunStatusTypes.FINISHED) {
            String reason = last == null || last.getFailure() == null ? null : last.getFailure().getMessage();
            throw new IllegalStateException("Patient tool run " + runId + " failed: " + reason);
        }
        return extractOutputsTransactional(runId);
    }

    /** Puts the input into the volume of the attempt, before its app starts. */
    public void stageInput(ExecutionAttemptEntity attempt) {
        Long runId = attempt.getStep().getRun().getId();
        Path input = inputs.remove(runId);
        if (input == null) {
            return;
        }
        try {
            if (!Files.isRegularFile(input)) {
                throw new IllegalStateException("No input file to hand to the app");
            }
            orchestrator.uploadFilesToVolume(runId, attempt.getId(), input, DATA_NAME);
            Log.infof("Handed %s of patient data to attempt %d", FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(input.toFile())), attempt.getId());
        } finally {
            FileUtils.deleteQuietly(input.toFile());
        }
    }

    /** Keeps what a patient tool wrote, before it counts as finished; nothing for any other attempt. */
    public void keepOutputs(Long attemptId) {
        Optional<Long> runId = findToolRunIdTransactional(attemptId);
        if (runId.isEmpty()) {
            return;
        }
        List<File> written = orchestrator.filesOf(runId.get(), attemptId).stream()
                .filter(file -> file.isFile() && !file.getName().startsWith("."))
                .toList();
        try {
            keepOutputFilesTransactional(attemptId, written);
            Log.infof("Kept %d outputs of patient tool attempt %d", written.size(), attemptId);
        } finally {
            written.forEach(FileUtils::deleteQuietly);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    Optional<Long> findToolRunIdTransactional(Long attemptId) {
        return attemptAO.findRunIdOfToolAttemptOptional(attemptId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void keepOutputFilesTransactional(Long attemptId, List<File> written) {
        executionFileBO.storeAndLinkFiles(attemptAO.findById(attemptId), written, ExecutionFileDirection.OUTPUT);
    }

    // An input the app never got goes with its run.
    public void discardInput(Long runId) {
        Optional.ofNullable(inputs.remove(runId)).ifPresent(input -> FileUtils.deleteQuietly(input.toFile()));
    }

    @Transactional
    public List<ExecutionRunDTO> listTransactional(Long cohortId, PatientToolRunType toolType) {
        return runAO.listToolRuns(cohortId, toolType, MAX_HISTORY).stream().map(run -> mapper.mapRunEntityToDTO(run, false)).toList();
    }

    @Transactional
    public byte[] zipOutputsTransactional(Long cohortId, Long runId) {
        ExecutionRunEntity run = runAO.findByIdAndCohortIdOptional(runId, cohortId)
                .orElseThrow(() -> new NotFoundException("Run " + runId + " not found for cohort " + cohortId));
        Map<String, File> outputs = executionFileBO.loadForRun(run.getId(), ExecutionFileDirection.OUTPUT);
        if (outputs.isEmpty()) {
            throw new NotFoundException("Run " + runId + " has no outputs yet");
        }
        try {
            return FileHelper.filesToZipByPath(outputs);
        } finally {
            outputs.values().forEach(FileUtils::deleteQuietly);
        }
    }

    @Transactional
    public void deleteAllForCohortTransactional(Long cohortId) {
        List<ExecutionRunEntity> runs = runAO.listByCohort(cohortId);
        runs.forEach(run -> {
            executionFileBO.deleteForRun(run.getId(), ExecutionFileDirection.OUTPUT);
            workflowExecutionBO.deleteTransactional(run.getId());
        });
        Log.infof("Deleted %d patient tool runs of cohort %d", runs.size(), cohortId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    List<Path> extractOutputsTransactional(Long runId) {
        Map<String, File> outputs = executionFileBO.loadForRun(runId, ExecutionFileDirection.OUTPUT);
        try {
            Path dir = Files.createTempDirectory("patient-tool-run-" + runId + "-");
            List<Path> extracted = new ArrayList<>();
            for (Map.Entry<String, File> output : outputs.entrySet()) {
                extracted.add(Files.move(output.getValue().toPath(), dir.resolve(output.getKey())));
            }
            return extracted;
        } catch (IOException e) {
            outputs.values().forEach(FileUtils::deleteQuietly);
            throw new UncheckedIOException("Could not hand over the outputs of patient tool run " + runId, e);
        }
    }
}
