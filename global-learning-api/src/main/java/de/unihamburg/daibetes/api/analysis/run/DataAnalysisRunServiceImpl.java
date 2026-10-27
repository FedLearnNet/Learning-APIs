package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.execution.orchestrator.DataAnalysisCreatePredictionDTO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
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
    DataAnalysisExecutionBO executionBO;

    @Inject
    DataAnalysisRunResultBO resultBO;

    @Inject
    DataAnalysisFileBO fileBO;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    Multi<DataAnalysisRunDTO> runChanges;

    @Override
    public Multi<DataAnalysisRunDTO> createDataAnalysisRun(DataAnalysisCreatePredictionDTO data) {
        return startAndSubscribe(null, data);
    }

    @Override
    public Multi<DataAnalysisRunDTO> getDataAnalysisRun(Long runId) {
        return subscribe(runId, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public DataAnalysisRunDTO stopRun(Long dataAnalysisId, Long runId) {
        return dataAnalysisBO.stopRun(dataAnalysisId, runId, userIdentity.getKeycloakId());
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
    public Multi<DataAnalysisRunDTO> createDataAnalysisWorkflowRun(Long dataAnalysisId, DataAnalysisCreatePredictionDTO data) {
        return startAndSubscribe(dataAnalysisId, data);
    }

    @Override
    @Transactional
    public Response downloadOutput(DataAnalysisRunModesEnum mode, String containerId) {
        String keycloakId = userIdentity.getKeycloakId();
        byte[] result = fileBO.downloadOutputTransactional(containerId, keycloakId);

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
        byte[] result = fileBO.downloadOutputForRun(experimentId, keycloakId);

        String filename = "result-workflow" + experimentId + "-files.zip";
        return Response.ok(result, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, result.length)
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    private Multi<DataAnalysisRunDTO> startAndSubscribe(Long dataAnalysisId, DataAnalysisCreatePredictionDTO data) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom().item(() -> executionBO.startTransactional(dataAnalysisId, data, keycloakId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(runId -> subscribe(runId, keycloakId));
    }

    private Multi<DataAnalysisRunDTO> subscribe(Long runId, String keycloakId) {
        Multi<DataAnalysisRunDTO> changes = runChanges
                .filter(run -> runId.equals(run.getId()) && keycloakId.equals(run.getKeycloakId()));
        Multi<DataAnalysisRunDTO> current = Uni.createFrom().item(() -> resultBO.findRunByIdTransactional(runId, keycloakId, false))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .toMulti();
        return Multi.createBy().merging().streams(changes, current);
    }
}
