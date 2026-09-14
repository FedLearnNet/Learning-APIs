package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RunMetricsRequestServiceImpl implements RunMetricsRequestService {

    @Inject
    RunMetricsRequestBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public RunMetricsRequestDTO getRequest(Long projectId, Long experimentId) {
        return bo.findForExperiment(experimentId, userIdentity.getKeycloakId())
                .orElseThrow(() -> new NotFoundException("No metrics request found for experiment " + experimentId));
    }

    @Override
    @Transactional
    public RunMetricsRequestDTO createRequest(Long projectId, Long experimentId) {
        return bo.createAndBroadcast(experimentId, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public EvaluationSummaryDTO getEvaluationSummary(Long projectId, Long experimentId) {
        return bo.evaluationSummary(experimentId, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response exportEvaluationCsv(Long projectId, Long experimentId) {
        String csv = bo.evaluationCsv(experimentId, userIdentity.getKeycloakId());
        return Response.ok(csv, "text/csv")
                .header("Content-Disposition",
                        "attachment; filename=\"evaluation-" + experimentId + ".csv\"")
                .build();
    }
}
