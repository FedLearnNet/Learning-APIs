package de.unihamburg.daibetes.api.project.training.result;

import bio.cosy.feddb.core.api.socket.RunResultRequestType;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RunResultRequestServiceImpl implements RunResultRequestService {

    @Inject
    RunResultRequestBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public RunResultRequestDTO getRequest(Long projectId, Long experimentId, RunResultRequestType type) {
        return bo.findForExperiment(experimentId, type, userIdentity.getKeycloakId())
                .orElseThrow(() -> new NotFoundException("No " + type + " request found for experiment " + experimentId));
    }

    @Override
    @Transactional
    public RunResultRequestDTO createRequest(Long projectId, Long experimentId, RunResultRequestType type) {
        return bo.createAndBroadcast(experimentId, type, userIdentity.getKeycloakId());
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
