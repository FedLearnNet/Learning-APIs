package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.run.UpdateRunDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.services.orch.dto.VolumeUploadForm;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.patient.tools.log.PatientToolRunLogBO;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageLogMapper;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import bio.cosy.feddb.local.services.orch.OrchVolumeServiceClient;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;


@ApplicationScoped
public class PatientToolRunBO
        extends BaseBo<PatientToolRunDTO,
        PatientToolRunEntity,
        PatientToolRunAO,
        PatientToolRunMapper> {

    @Inject
    Instance<PatientToolRunBO> self;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    @RestClient
    OrchVolumeServiceClient volumeClient;

    @Inject
    @RestClient
    GlobalAPIStoreService globalAPIStoreService;

    @Inject
    WorkflowOrchestratorBO workflowOrchestrator;

    @Inject
    PatientToolRunFileBO fileBO;

    @Inject
    PatientToolRunLogBO logBO;

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    CohortAO cohortAO;

    @Inject
    PatientToolRunEventSender eventSender;

    @Inject
    @Channel(PatientToolRunEventSender.PATIENT_TOOL_RUN_CHANNEL)
    Multi<PatientToolRunProgressDTO> runEvents;

    static final Long GROUP_ID = -2L;
    public static final String DATA_NAME = "data";
    static final String WS_PATH = "patient/tool/run";
    public static final String SERVER_PROCESS = "server";
    private static final int MAX_HISTORY = 50;

    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(30);


    public PatientToolRunDTO start(Long cohortId, Long globalAppVersionId, LinkedHashMap<String, Object> hyperParams,
                                   PatientToolRunType toolType, Path input) {
        if (globalAppVersionId == null) {
            throw new BadRequestException("No app version selected");
        }
        FederatedAppDetailDTO app = globalAPIStoreService.getAppByVersion(globalAppVersionId);
        if (app == null || StringUtils.isEmpty(app.getImageName())) {
            throw new NotFoundException("No image found for app version id: " + globalAppVersionId);
        }
        if (app.getAppConfig() == null || app.getAppConfig().getInput() == null || app.getAppConfig().getInput().isEmpty()) {
            throw new BadRequestException("App '" + app.getName() + "' declares no input to receive the patient data");
        }

        Long runId = self.get().createRun(cohortId, globalAppVersionId, hyperParams, toolType, app);
        Log.infof("Created patient tool run %d (%s) for image %s", runId, toolType, app.getImageName());
        logStep(runId, "Created the run for " + app.getName());

        String apiKey = toolApiKeyService.issue(Scope.PATIENT_TOOL_RUN, runId);
        Infrastructure.getDefaultWorkerPool().execute(() -> launch(runId, app, apiKey, input));
        return self.get().findRun(runId).orElseThrow();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Long createRun(Long cohortId, Long globalAppVersionId, LinkedHashMap<String, Object> hyperParams,
                          PatientToolRunType toolType, FederatedAppDetailDTO app) {
        PatientToolRunEntity run = new PatientToolRunEntity();
        run.setRunStatus(RunStatusTypes.PENDING);
        run.setStartedAt(new Date());
        run.setImage(app.getImageName());
        run.setToolType(toolType);
        run.setGlobalAPPVersionId(globalAppVersionId);
        run.setHyperParams(hyperParams);
        run.setCohort(cohortId == null ? null : cohortAO.findByIdOptional(cohortId)
                .orElseThrow(() -> new NotFoundException("Cohort " + cohortId + " not found")));
        run.setAppName(app.getName());
        ao.persist(run);
        return run.getId();
    }

    public List<Path> runAndAwait(Long cohortId, Long globalAppVersionId, LinkedHashMap<String, Object> hyperParams,
                                  PatientToolRunType toolType, Path input) {
        Long runId = start(cohortId, globalAppVersionId, hyperParams, toolType, input).getId();
        PatientToolRunProgressDTO last;
        try {
            last = progress(runId).collect().last().await().atMost(RUN_TIMEOUT);
        } catch (TimeoutException e) {
            fail(runId, "The app did not finish within " + RUN_TIMEOUT);
            cleanupContainer(runId);
            throw new IllegalStateException("Patient tool run " + runId + " did not finish within " + RUN_TIMEOUT, e);
        }
        if (statusOf(last) != RunStatusTypes.FINISHED) {
            String reason = last == null || last.getStatus() == null ? null : last.getStatus().getLastError();
            throw new IllegalStateException("Patient tool run " + runId + " failed: " + reason);
        }
        return fileBO.extractOutputs(runId);
    }

    void launch(Long runId, FederatedAppDetailDTO app, String apiKey, Path input) {
        try {
            uploadInput(runId, input);
            logStep(runId, "Starting the app container - the first run pulls the image, which can take a while");
            String containerId = workflowOrchestrator.executeWorkflow(startDTO(runId, app, apiKey), WS_PATH);
            self.get().markStarted(runId, containerId);
            Log.infof("Started container %s for patient tool run %d", containerId, runId);
            publishStatus(runId);
            logStep(runId, "Container started, waiting for the app to connect");
        } catch (Exception e) {
            Log.errorf(e, "Could not start patient tool run %d", runId);
            toolApiKeyService.revoke(Scope.PATIENT_TOOL_RUN, runId);
            fail(runId, "Could not start the app: " + e.getMessage());
            cleanupContainer(runId);
        } finally {
            FileUtils.deleteQuietly(input == null ? null : input.toFile());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markStarted(Long runId, String containerId) {
        PatientToolRunEntity run = ao.findById(runId, LockModeType.PESSIMISTIC_WRITE);
        run.setContainerId(containerId);
        // The app may already have connected and moved the run on
        if (run.getRunStatus() == RunStatusTypes.PENDING) {
            run.setRunStatus(RunStatusTypes.STARTED);
        }
    }

    private void uploadInput(Long runId, Path input) {
        if (input == null || !Files.isRegularFile(input)) {
            throw new IllegalStateException("No input file to hand to the app");
        }
        logStep(runId, "Handing the patient data to the app ("
                + FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(input.toFile())) + ")");
        VolumeUploadForm form = new VolumeUploadForm(input.toFile(), DATA_NAME);
        try (Response response = volumeClient.uploadFilesIds(GROUP_ID, runId, form)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("Uploading the input failed: " + response.readEntity(String.class));
            }
        }
    }

    private StartWorkflowNodeDTO startDTO(Long runId, FederatedAppDetailDTO app, String apiKey) {
        StartWorkflowNodeDTO dto = new StartWorkflowNodeDTO();
        dto.setWorkflowId(GROUP_ID);
        dto.setWorkflowNodeId(runId);
        dto.setAppImage(app.getImageName());
        dto.setIsFistNode(true);
        dto.setEnableRemoteResultSaving(false);
        dto.setNeedsInternetAccess(Boolean.TRUE.equals(app.getNeedsInternetAccess()));
        dto.setNeedsHostAccess(Boolean.TRUE.equals(app.getNeedsHostAccess()));
        dto.setEnvironments(new ArrayList<>(List.of(
                "APP_ID=" + runId,
                "APP_API_KEY=" + apiKey
        )));
        return dto;
    }

    public void completeRun(Long runId) {
        Optional<PatientToolRunDTO> run = self.get().findRun(runId);
        if (run.isEmpty() || RunStatusTypes.isFinalStatus(run.get().getRunStatus())) {
            return;
        }
        logStep(runId, "The app finished, collecting its outputs");
        List<File> downloaded = List.of();
        try {
            downloaded = workflowOrchestrator.getFiles(GROUP_ID, runId);
            Map<String, File> files = new LinkedHashMap<>();
            for (File file : downloaded) {
                if (file.isFile() && !file.getName().startsWith(".")) {
                    files.putIfAbsent(file.getName(), file);
                }
            }
            FileDTO stored = self.get().finishWithOutputs(runId, files);
            Log.infof("Patient tool run %d finished with outputs %s", runId, files.keySet());
            logStep(runId, "Stored " + files.size() + " outputs (" + String.join(", ", files.keySet()) + ") as zip, "
                    + FileUtils.byteCountToDisplaySize(stored.getSize()));
            publishStatus(runId);
        } catch (Exception e) {
            Log.errorf(e, "Could not collect the outputs of patient tool run %d", runId);
            fail(runId, "The app finished but its outputs could not be collected: " + e.getMessage());
        } finally {
            downloaded.forEach(FileUtils::deleteQuietly);
            cleanupContainer(runId);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public FileDTO finishWithOutputs(Long runId, Map<String, File> files) {
        PatientToolRunEntity run = ao.findById(runId, LockModeType.PESSIMISTIC_WRITE);
        PatientToolRunFileEntity outputs = fileBO.storeOutputs(run, files);
        run.setRunStatus(RunStatusTypes.FINISHED);
        run.setProgress(1f);
        run.setFinishedAt(new Date());
        return fileBO.entityToDto(outputs);
    }

    public void fail(Long runId, String message) {
        if (self.get().markFailed(runId, message)) {
            logStep(runId, message, "ERROR");
            publishStatus(runId);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean markFailed(Long runId, String message) {
        PatientToolRunEntity run = ao.findById(runId, LockModeType.PESSIMISTIC_WRITE);
        if (run == null || RunStatusTypes.isFinalStatus(run.getRunStatus())) {
            return false;
        }
        run.setRunStatus(RunStatusTypes.ERROR);
        run.setLastError(StringUtils.abbreviate(message, 2000));
        run.setFinishedAt(new Date());
        return true;
    }

    public void logStep(Long runId, String message) {
        logStep(runId, message, "INFO");
    }

    private void logStep(Long runId, String message, String severity) {
        try {
            self.get().appendStepInNewTransaction(runId, message, severity);
        } catch (Exception e) {
            Log.warnf("Could not log a step of patient tool run %d: %s", runId, e.getMessage());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void appendStepInNewTransaction(Long runId, String message, String severity) {
        appendStep(runId, message, severity);
    }

    @Transactional
    public void appendStep(Long runId, String message) {
        appendStep(runId, message, "INFO");
    }

    private void appendStep(Long runId, String message, String severity) {
        RunMessageLogDTO log = new RunMessageLogDTO();
        log.setProcess(SERVER_PROCESS);
        log.setType(RunMessageTypes.LOG);
        log.setSeverity(severity);
        log.setMessage(message);
        logBO.create(log, runId);
    }

    public List<PatientToolRunSummaryDTO> listRuns(Long cohortId, PatientToolRunType toolType) {
        return ao.findByCohort(cohortId, toolType, MAX_HISTORY).stream()
                .map(run -> mapper.toSummaryDto(run, fileBO.findByRun(run.getId()).orElse(null)))
                .toList();
    }

    public void cleanupContainer(Long runId) {
        cleanupContainer(runId, self.get().findRun(runId).map(PatientToolRunDTO::getContainerId).orElse(null));
    }

    private void cleanupContainer(Long runId, String containerId) {
        try {
            if (containerId != null) {
                workflowOrchestrator.cleanup(containerId, true);
            } else {
                workflowOrchestrator.cleanupWorkflowNode(runId);
            }
        } catch (Exception e) {
            Log.warnf("Could not clean up the container of patient tool run %d: %s", runId, e.getMessage());
        }
    }


    @Transactional
    public void deleteAllForCohort(Long cohortId) {
        List<PatientToolRunEntity> runs = ao.findAllByCohort(cohortId);
        if (runs.isEmpty()) {
            return;
        }
        for (PatientToolRunEntity run : runs) {
            if (!RunStatusTypes.isFinalStatus(run.getRunStatus())) {
                toolApiKeyService.revoke(Scope.PATIENT_TOOL_RUN, run.getId());
                cleanupContainer(run.getId(), run.getContainerId());
            }
        }
        List<Long> runIds = runs.stream().map(PatientToolRunEntity::getId).toList();
        fileBO.deleteAllForRuns(runIds);
        logBO.deleteAllForRuns(runIds);
        ao.deleteByIds(runIds);
        Log.infof("Deleted %d patient tool runs of cohort %d", runIds.size(), cohortId);
    }

    private void publishStatus(Long runId) {
        try {
            eventSender.send(self.get().snapshot(runId, Long.MAX_VALUE));
        } catch (Exception e) {
            Log.debugf("Could not publish the status of patient tool run %d: %s", runId, e.getMessage());
        }
    }

    public Multi<PatientToolRunProgressDTO> progress(Long runId) {
        Multi<PatientToolRunProgressDTO> live = runEvents.filter(event -> runId.equals(event.getRunId()));
        Multi<PatientToolRunProgressDTO> current = Multi.createFrom().item(() -> self.get().snapshot(runId, 0L))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        return untilTerminal(Multi.createBy().merging().streams(live, current));
    }

    private static Multi<PatientToolRunProgressDTO> untilTerminal(Multi<PatientToolRunProgressDTO> events) {
        return Multi.createFrom().emitter(emitter -> {
            Cancellable subscription = events.subscribe().with(
                    event -> {
                        emitter.emit(event);
                        if (RunStatusTypes.isFinalStatus(statusOf(event))) {
                            emitter.complete();
                        }
                    },
                    emitter::fail,
                    emitter::complete);
            emitter.onTermination(subscription::cancel);
        });
    }

    private static RunStatusTypes statusOf(PatientToolRunProgressDTO event) {
        return event == null || event.getStatus() == null ? null : event.getStatus().getRunStatus();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PatientToolRunProgressDTO snapshot(Long runId, long afterLogId) {
        PatientToolRunEntity run = ao.findById(runId);
        if (run == null) {
            throw new NotFoundException("Patient tool run " + runId + " not found");
        }
        List<RunMessageLogDTO> logs = logMapper.dtosToLogDtos(logBO.findByRunAfter(runId, afterLogId));
        Optional<PatientToolRunFileEntity> outputs = run.getRunStatus() == RunStatusTypes.FINISHED
                ? fileBO.findByRun(runId)
                : Optional.empty();
        return mapper.toProgressDto(runId, mapper.entityToStatusDto(run), logs,
                outputs.map(fileBO::describeOutputs).orElse(List.of()), outputs.isPresent());
    }

    public FileResult loadOutputs(Long cohortId, Long runId) {
        PatientToolRunEntity run = runId == null ? null : ao.findById(runId);
        if (run == null || cohortId == null || run.getCohort() == null
                || !Objects.equals(run.getCohort().getId(), cohortId)) {
            throw new NotFoundException("Run " + runId + " not found for cohort " + cohortId);
        }
        PatientToolRunFileEntity outputs = fileBO.findByRun(runId)
                .orElseThrow(() -> new NotFoundException("Run " + runId + " has no outputs yet"));
        return fileBO.loadFileResult(outputs);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<PatientToolRunDTO> findRun(Long runId) {
        return ao.findByIdOptional(runId).map(mapper::entityToDto);
    }

    public Optional<PatientToolRunEntity> findPatientToolRunEntityByIdOptional(Long stepId) {
        return ao.findByIdOptional(stepId);
    }

    public void persistStep(Long stepId, RunStatusTypes status) {
        PatientToolRunEntity step = ao.findById(stepId, LockModeType.PESSIMISTIC_WRITE);
        step.setRunStatus(status);
        ao.persist(step);
        eventSender.sendStatus(mapper.entityToStatusDto(step));
    }

    public void persistStep(Long stepId, UpdateRunDTO dto) {
        PatientToolRunEntity step = ao.findById(stepId, LockModeType.PESSIMISTIC_WRITE);
        step.setRunStatus(dto.getStatus());
        step.setProgress(dto.getProgress());
        if (dto.getStatus().equals(RunStatusTypes.ERROR)) {
            step.setLastError(dto.getError());
        }
        ao.persist(step);
        eventSender.sendStatus(mapper.entityToStatusDto(step));
    }

    public StartRunDTO getStartup(Long runId) {
        Log.infof("Starting for patient tool runId: %d", runId);
        Optional<PatientToolRunEntity> stepOptional = ao.findByIdOptional(runId);
        if (stepOptional.isEmpty()) {
            throw new IllegalArgumentException("Patient tool run with id " + runId + " not found");
        }
        PatientToolRunDTO run = mapper.entityToDto(stepOptional.get());
        LinkedHashMap<String, String> dataInputs = new LinkedHashMap<>();
        String name = globalAPIStoreService.getAppByVersion(run.getGlobalAPPVersionId())
                .getAppConfig()
                .getInput()
                .getFirst()
                .getVariableName();
        if (StringUtils.isEmpty(name)) {
            throw new RuntimeException("Input variable name is empty for app version id: " + run.getGlobalAPPVersionId());
        }
        dataInputs.put(name, DATA_NAME);

        StartRunDTO testDto = new StartRunDTO();
        testDto.setId(runId);
        testDto.setInputData(null);
        testDto.setInputFilePaths(dataInputs);
        testDto.setStatus(RunStatusTypes.PENDING);
        testDto.setHyperParams(run.getHyperParams());
        return testDto;
    }
}
