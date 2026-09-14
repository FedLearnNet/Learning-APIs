package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisOrchestrator;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultSender;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import de.unihamburg.daibetes.services.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

@ApplicationScoped
public class DataAnalysisRunBO extends DataAnalysisOrchestrator {

    // Single source of truth for the overhead flag; runtime is always exposed regardless.
    @Inject
    @ConfigProperty(name = "posymed.runtime.overhead.enabled", defaultValue = "false")
    boolean overheadEnabled;

    @Inject
    @RestClient
    OrchContainerServiceClient containerClient;

    @Inject
    @RestClient
    OrchVolumeServiceClient volumeClient;

    @Inject
    @RestClient
    OrchWorkflowServiceClient workflowClient;

    @Inject
    @RestClient
    OrchDockerServiceClient dockerClient;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    DataAnalysisPredictionBO modelPredictionBO;

    @Inject
    DataAnalysisFileBO modelWorkflowFileBO;

    @Inject
    ModelSubBO modelSubBO;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    DataAnalysisMonitor monitor;
    @Inject
    DataAnalysisResultSender resultSender;

    public DataAnalysisPredictionDTO startModel(DataAnalysisCreatePredictionDTO createModel, String keycloakId) throws IllegalArgumentException {
        return startModel(createModel, keycloakId, null);
    }

    @Transactional
    public DataAnalysisPredictionDTO startModel(DataAnalysisCreatePredictionDTO createModel, String keycloakId, Long dataAnalysisId) throws IllegalArgumentException {
        DataAnalysisPredictionDTO prediction = createPrediction(createModel, keycloakId, dataAnalysisId);

        String image = prediction.getImageName();
        String apiKey = toolApiKeyService.issue(Scope.MODEL_PREDICTION_RUN, prediction.getId());

        try {
            String containerId = execute(createModel, prediction.getId(), image, apiKey);
            prediction.setContainerId(containerId);
        } catch (ClientWebApplicationException e) {
            String errorMessage = "Error during model execution: " + e.getMessage();
            String responseBody = e.getResponse() != null ? e.getResponse().readEntity(String.class) : null;
            if (e.getResponse().getStatus() == 404) {
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

        prediction = modelPredictionBO.update(prediction);
        if (!RunStatusTypes.isFinalStatus(prediction.getStatus())) {
            //monitor.start(prediction.getId());
        }
        return prediction;
    }

    public DataAnalysisPredictionDTO createPrediction(DataAnalysisCreatePredictionDTO createModel, String keycloakId, Long dataAnalysisId) throws IllegalArgumentException {
        DataAnalysisEntity dataAnalysis = dataAnalysisBO.findEntityById(dataAnalysisId, keycloakId);

        if (createModel.getModelSubId() == null && createModel.getAppVersionId() != null) {
            FederatedAppVersionDTO version = federatedAppVersionBO.getById(createModel.getAppVersionId());
            Log.info("Starting startModel with appVersionId: " + createModel.getAppVersionId());
            String image = version.getImageName();
            Log.info("App details - ID: " + version.getId() + ", Image: " + image);
            DataAnalysisPredictionDTO prediction;
            if (dataAnalysis == null) {
                prediction = modelPredictionBO.createForApp(createModel.getAppVersionId(), createModel, keycloakId);
            } else {
                prediction = modelPredictionBO.createForApp(dataAnalysis, createModel.getAppVersionId(), createModel, keycloakId);
            }
            prediction.setImageName(image);
            return prediction;
        }

        Log.info("Starting startModel with modelSubId: " + createModel.getModelSubId());
        ModelSubDTO model = modelSubBO.findBySubIdOrModelVersionId(createModel.getModelSubId(), createModel.getModelVersionId(), keycloakId);
        String image = model.getImageName();
        Log.info("Model details - ID: " + model.getId() + ", Image: " + image);
        Log.info("Creating prediction for model ID: " + model.getId());
        DataAnalysisPredictionDTO prediction;
        Long modelId = model.getModelId();
        if (dataAnalysis == null) {
            prediction = modelPredictionBO.create(modelId, createModel, keycloakId);
        } else {
            prediction = modelPredictionBO.create(dataAnalysis, modelId, createModel, keycloakId);
        }
        prediction.setImageName(image);
        return prediction;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String uploadOutputTransactional(Long runId, AppRunUploadData req, String keycloakId) {
        Log.info("Starting cleanup for runId: " + runId);
        DataAnalysisPredictionEntity prediction =
                modelPredictionBO.finishEntityById(runId, req.getFieldsNullsafe(), req.getMeta());
        modelWorkflowFileBO.storePrediction(prediction, req.getFiles());
        DataAnalysisPredictionDTO dto = modelPredictionBO.entityToDto(prediction);
        if (dto.getMeta() != null) {
            dto.getMeta().withOverheadVisibility(overheadEnabled);
        }
        resultSender.sendMessage(dto);
        return prediction.getContainerId();
    }

    public void uploadOutput(Long runId, AppRunUploadData req, String keycloakId) {
        String containerId = uploadOutputTransactional(runId, req, keycloakId);
        cleanup(containerId);
    }

    public void cleanup(String containerId) {
        Log.info("Cleaning up workflow for containerId: " + containerId);
        getContainerClient().cleanupWorkflow(containerId, true);
    }

    @Override
    protected ContainerServiceClient getContainerClient() {
        return containerClient;
    }

    @Override
    protected WorkflowServiceClient getWorkflowClient() {
        return workflowClient;
    }

    @Override
    protected VolumeServiceClient getVolumeClient() {
        return volumeClient;
    }

    @Override
    protected DockerServiceClient getDockerClient() {
        return dockerClient;
    }

}
