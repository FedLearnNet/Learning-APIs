package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisStopPredictionDTO;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepBO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class DataAnalysisRunServiceImpl implements DataAnalysisRunService {
    public static final String DATA_ANALYSIS_RUN_CHANNEL = "data-analysis-run-info";

    @Inject
    UserIdentity userIdentity;

    @Inject
    DataAnalysisRunBO dataAnalysisRunBO;

    @Inject
    DataAnalysisWorkflowRunStepBO workflowRunStepBO;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    DataAnalysisPredictionBO dataAnalysisPredictionBO;

    @Inject
    DataAnalysisFileBO dataAnalysisFileBO;

    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    Multi<DataAnalysisPredictionDTO> processInfo;


    @Override
    public Multi<DataAnalysisPredictionDTO> createDataAnalysisRun(DataAnalysisCreatePredictionDTO data) {
        String keycloakId = userIdentity.getKeycloakId();
        Log.info("Keycloak ID: " + keycloakId);
        Uni.createFrom().item(() -> dataAnalysisRunBO.startModel(data, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(result -> {
                });
        return filterCurrentStream(keycloakId);
    }

    @Override
    public Multi<DataAnalysisPredictionDTO> getDataAnalysisRun(Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return Uni.createFrom().item(() -> dataAnalysisPredictionBO.getById(id, keycloakId)) // blocking
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onItem().transformToMulti(current -> {
                    Multi<DataAnalysisPredictionDTO> first = Multi.createFrom().item(current);
                    if (current != null && current.isFinished()) {
                        return first;
                    }
                    return Multi.createBy().concatenating().streams(first, filterCurrentStream(id));
                });
    }

    @Override
    @Transactional
    public DataAnalysisPredictionDTO stopRun(Long dataAnalysisId, Long id, DataAnalysisStopPredictionDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        Log.info("Keycloak ID: " + keycloakId);
        return dataAnalysisBO.stopRun(dataAnalysisId, id, dto, keycloakId);
    }

    @Override
    @Transactional
    public Response deleteRun(Long dataAnalysisId, Long id, Long workflowId) {
        String keycloakId = userIdentity.getKeycloakId();
        Log.info("Keycloak ID: " + keycloakId);
        dataAnalysisBO.deleteRun(dataAnalysisId, id, workflowId, keycloakId);
        return Response.ok().build();
    }

    @Override
    public Multi<DataAnalysisPredictionDTO> createDataAnalysisWorkflowRun(Long dataAnalysisId, DataAnalysisCreatePredictionDTO data) {
        String keycloakId = userIdentity.getKeycloakId();
        Log.info("Keycloak ID: " + keycloakId);
        Uni.createFrom().item(() -> dataAnalysisBO.createModelRun(dataAnalysisId, data, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(result -> {
                });
        return filterCurrentStream(keycloakId);
    }

    @Override
    public Multi<DataAnalysisPredictionDTO> getDataAnalysisWorkflowRun(Long dataAnalysisId, Long workflowId) {
        return filterCurrentStream(dataAnalysisId, workflowId);
    }

    @Override
    public Response uploadOutput(DataAnalysisRunModesEnum mode, Long runId, AppRunUploadData req) {
        String keycloakId = userIdentity.getKeycloakId();
        if (mode.equals(DataAnalysisRunModesEnum.WORKFLOW)) {
            workflowRunStepBO.uploadOutput(runId, req, keycloakId);
        } else {
            dataAnalysisRunBO.uploadOutput(runId, req, keycloakId);
        }
        return Response.status(Response.Status.CREATED).entity(null).build();
    }

    @Override
    @Transactional
    public Response downloadOutput(DataAnalysisRunModesEnum mode, String containerId) {
        String keycloakId = userIdentity.getKeycloakId();
        byte[] result = null;
        if (mode.equals(DataAnalysisRunModesEnum.PREDICTION)) {
            result = dataAnalysisPredictionBO.downloadOutput(keycloakId, containerId);
        } else {
            result = workflowRunStepBO.downloadOutput(keycloakId, containerId);
        }

        String filename = "result-" + containerId + "-files.zip";
        return Response.ok(result, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, result.length)
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    @Override
    @Transactional
    public Response downloadWorkflowOutput(Long experimentId) {
        String keycloakId = userIdentity.getKeycloakId();
        byte[] result = dataAnalysisFileBO.downloadOutputForWorkflow(experimentId, keycloakId);

        String filename = "result-workflow" + experimentId + "-files.zip";
        return Response.ok(result, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, result.length)
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    private Multi<DataAnalysisPredictionDTO> filterCurrentStream(Long id) {
        return processInfo
                .filter(p -> p.getId() != null && p.getId().equals(id));
    }

    private Multi<DataAnalysisPredictionDTO> filterCurrentStream(String keycloakId) {
        return processInfo
                .filter(p -> p.getKeycloakId() != null && p.getKeycloakId().equals(keycloakId));
    }

    private Multi<DataAnalysisPredictionDTO> filterCurrentStream(Long dataAnalysisId, Long workflowId) {
        return processInfo.filter(p ->
                p.getWorkflowId() != null
                        && p.getWorkflowId().equals(workflowId)
                        && p.getDataAnalysisId() != null
                        && p.getDataAnalysisId().equals(dataAnalysisId));
    }
}
