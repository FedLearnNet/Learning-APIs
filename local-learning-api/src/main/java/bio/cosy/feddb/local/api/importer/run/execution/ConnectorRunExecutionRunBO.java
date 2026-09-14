package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisOrchestrator;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartAppDTO;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.read.TabularFileReaderBO;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileUploadBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.services.orch.OrchContainerServiceClient;
import bio.cosy.feddb.local.services.orch.OrchDockerServiceClient;
import bio.cosy.feddb.local.services.orch.OrchVolumeServiceClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
public class ConnectorRunExecutionRunBO extends DataAnalysisOrchestrator {
    public static final String WS_WORKFLOW_PATH = "/connectors/run/execution";

    @Inject
    @RestClient
    OrchContainerServiceClient containerClient;

    @Inject
    @RestClient
    OrchVolumeServiceClient volumeClient;

    @Inject
    @RestClient
    OrchDockerServiceClient dockerClient;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    ConnectorRunStepBO stepBo;

    @Inject
    TabularFileReaderBO fileHandlerBO;

    @Inject
    ConnectorRunExecutionOutputAwaiter outputAwaiter;

    @Inject
    ConnectorFileUploadBO uploadBO;
    //@Inject
    //DataAnalysisResultSender resultSender;


    @ActivateRequestContext
    @Transactional
    public ConnectorRunStepDTO startModel(ConnectorTransformerDTO transformer, ConnectorRunStepDTO prediction, String keycloakId) throws IllegalArgumentException {
        String image = transformer.getAppImage();
        String apiKey = toolApiKeyService.issue(Scope.CONNECTOR_RUN, prediction.getId());

        DataAnalysisCreatePredictionDTO createModel = new DataAnalysisCreatePredictionDTO();
        createModel.setKeycloakId(keycloakId);
        createModel.setAppVersionId(transformer.getAppVersionId());
        createModel.setHyperParams(transformer.getHyperparams());
        createModel.setId(transformer.getId());
        try {
            String containerId = execute(createModel, prediction.getId(), image, apiKey);
            prediction.setContainerId(containerId);
            prediction.setStatus(RunStatusTypes.INITIALIZED);
        } catch (ClientWebApplicationException e) {
            String errorMessage = "Error during model execution: " + e.getMessage();
            String responseBody = e.getResponse() != null ? e.getResponse().readEntity(String.class) : null;
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                errorMessage = "Model image not found: " + image;
            } else if (responseBody != null) {
                errorMessage += " | Response: " + responseBody;
            }
            Log.error(errorMessage, e);
            prediction.setLastError(errorMessage);
            prediction.setStatus(RunStatusTypes.ERROR);
        } catch (Exception e) {
            Log.error("Error creating model prediction: " + e.getMessage(), e);
            prediction.setLastError(e.getMessage());
            prediction.setStatus(RunStatusTypes.ERROR);
            throw e;
        }

        prediction = stepBo.update(prediction);
        return prediction;
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String uploadOutputTransactional(Long runId, AppRunUploadData req, String keycloakId) {
        Log.info("Starting cleanup for runId: " + runId);
        TableData outputData = loadUploadedOutputData(req);

        // A batched transformer keeps one container for the whole import, so this upload is the
        // result of one batch rather than the end of the step: hand it to whoever is waiting and
        // leave the step running for the next batch. The app echoes the id it was started with,
        // which is how the batch is identified - the URL still carries the step id, so the step's
        // API key keeps working across every batch.
        if (outputAwaiter.completeBatch(req.getRunId(), outputData)) {
            Log.debugf("Batch %d of step %d delivered", req.getRunId(), runId);
            return null;
        }
        LinkedHashMap<String, Object> storedOutputs = storeUploadedOutputs(runId, req, keycloakId);
        ConnectorRunStepDTO prediction = stepBo.finishEntityById(runId);
        outputAwaiter.complete(runId, prediction, outputData, storedOutputs);
        return prediction.getContainerId();
    }

    private LinkedHashMap<String, Object> storeUploadedOutputs(
            Long runId, AppRunUploadData req, String keycloakId) {
        LinkedHashMap<String, Object> stored = new LinkedHashMap<>();
        Long cohortId = outputAwaiter.cohortOf(runId).orElse(null);
        if (cohortId == null) {
            Log.debugf("No cohort registered for step %d, not storing its app outputs", runId);
            return stored;
        }

        int index = 0;
        for (FileUpload upload : req.getFilesNullsafe()) {
            if (upload == null || upload.uploadedFile() == null) {
                continue;
            }
            String key = AppRunUploadData.getKey(upload);
            if (key == null || key.isBlank()) {
                key = "output" + index;
            }
            index++;
            String fileName = AppRunUploadData.getName(upload);
            if (fileName == null || fileName.isBlank()) {
                fileName = upload.fileName();
            }
            try {
                ConnectorFilesDTO file = uploadBO.uploadFileForCohort(
                        cohortId, upload.uploadedFile().toFile(), fileName, keycloakId, null);
                stored.put(key, file.getId());
                Log.infof("Stored app output '%s' of step %d as connector file %d",
                        key, runId, file.getId());
            } catch (Exception e) {
                Log.errorf("App output '%s' (%s) of step %d could not be stored: %s",
                        key, fileName, runId, e.getMessage());
            }
        }
        return stored;
    }

    public void uploadOutput(Long runId, AppRunUploadData req, String keycloakId) {
        try {
            String containerId = uploadOutputTransactional(runId, req, keycloakId);
            if (containerId == null || containerId.isBlank()) {
                // A batch result: the container stays up for the next batch of the same step.
                return;
            }
            cleanup(containerId);
        } catch (Exception e) {
            Log.errorf(e, "Failed to process uploaded connector output for step %d", runId);
            stepBo.updateAndNotify(runId, RunStatusTypes.ERROR,
                    "Failed to process uploaded connector output: " + e.getMessage());
        }
    }

    public void cleanup(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            Log.debug("No container to clean up");
            return;
        }
        Log.info("Cleaning up workflow for containerId: " + containerId);
        getContainerClient().cleanupWorkflow(containerId, true);
    }

    @Override
    protected ContainerServiceClient getContainerClient() {
        return containerClient;
    }

    @Override
    protected WorkflowServiceClient getWorkflowClient() {
        return null;
    }

    @Override
    protected VolumeServiceClient getVolumeClient() {
        return volumeClient;
    }

    @Override
    protected DockerServiceClient getDockerClient() {
        return dockerClient;
    }

    public String execute(DataAnalysisCreatePredictionDTO model, Long id, String image, String apiKey) throws IllegalArgumentException {
        Long identifier = model.getModelSubId();
        if (identifier == null) {
            identifier = model.getAppVersionId();
        }
        StartAppDTO dto = new StartAppDTO();
        dto.setGroupId(id);
        dto.setIdentifier(identifier);
        dto.setAppImage(image);
        dto.setEnvironments(List.of(
                "APP_ID=" + id,
                "APP_API_KEY=" + apiKey
        ));
        // changeUrl is set to true to allow the container to access the ModelAppServer
        // Path is set to "model/run" to allow the container to access the ModelAppServer
        // The serverPort is set to the port of the ModelAppServer
        String path = WS_WORKFLOW_PATH;
        CreateContainerResponseDTO resp = getContainerClient().startContainer(dto, true, true, path, serverPort);
        String containerId = resp.getId();
        if (containerId == null) {
            throw new IllegalStateException("Failed to start container for model " + image);
        }
        return containerId;
    }

    private TableData loadUploadedOutputData(AppRunUploadData req) {
        List<FileUpload> uploads = req.getFilesNullsafe();
        if (uploads.isEmpty()) {
            throw new IllegalArgumentException("No output file was uploaded");
        }

        for (FileUpload upload : uploads) {
            if (upload == null || upload.uploadedFile() == null) {
                continue;
            }

            FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
            settings.setFileType(FileParsingType.CSV);
            settings.setDelimiter(",");
            settings.setHasHeader(true);
            settings.setFirstSheetOnly(true);
            File uploadedFile = upload.uploadedFile().toFile();
            TableData outputData = fileHandlerBO.getFirstTableData(uploadedFile, settings);
            if (outputData != null) {
                return outputData;
            }
        }

        throw new IllegalArgumentException("Uploaded output could not be converted to tabular data");
    }
}
