package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class RunServiceImpl implements RunService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    TestRunBO testRunBO;


    @Override
    public List<ExecutionRunDetailDTO> listTest(Long appId) {
        return testRunBO.listTransactional(appId, ExecutionMode.SINGLE, false, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO retrieveTest(Long appId, Long id) {
        return testRunBO.findRunTransactional(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public List<ExecutionRunDetailDTO> listFederatedRuns(Long appId) {
        return testRunBO.listTransactional(appId, ExecutionMode.FEDERATED, false, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO retrieveFederatedRun(Long appId, Long id) {
        return testRunBO.findRunTransactional(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public List<ExecutionRunDetailDTO> listExperiment(Long appId) {
        return testRunBO.listTransactional(appId, ExecutionMode.SINGLE, true, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionRunDetailDTO retrieveExperiment(Long appId, Long id) {
        return testRunBO.findExperimentTransactional(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public ExecutionExperimentDTO updateExperiment(Long appId, Long id, ExecutionExperimentDTO dto) {
        return testRunBO.updateExperimentTransactional(appId, id, dto, userIdentity.getKeycloakId());
    }

    @Override
    public Response createExperiment(Long appId, CreateExperimentDTO create) {
        ExecutionRunDetailDTO dto = testRunBO.startExperimentTransactional(appId, create, userIdentity.getKeycloakId());
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }
}
