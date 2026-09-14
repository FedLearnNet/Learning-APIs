package de.unihamburg.daibetes.api.runs;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.runs.experiment.CreateExperimentDTO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentBO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDTO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDetailDTO;
import de.unihamburg.daibetes.api.runs.experiment.message.ExperimentRunMessageBO;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunDTO;
import de.unihamburg.daibetes.api.runs.test.TestRunBO;
import de.unihamburg.daibetes.api.runs.test.TestRunDTO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunBO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunDTO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedParticipantMessageBO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageBO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageDTO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantBO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantDTO;
import de.unihamburg.daibetes.api.runs.test.message.TestRunMessageBO;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class RunServiceImpl implements RunService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    TestRunBO testRunBO;

    @Inject
    ExperimentBO experimentBO;

    @Inject
    ExperimentRunMessageBO experimentRunMessageBO;

    @Inject
    TestRunMessageBO testRunMessageBO;

    @Inject
    FederatedTestRunBO federatedTestRunBO;

    @Inject
    FederatedParticipantBO FederatedParticipantBO;

    @Inject
    FederatedParticipantMessageBO federatedParticipantMessageBO;

    @Inject
    FederatedRoundMessageBO federatedRoundMessageBO;

    @Override
    public List<TestRunDTO> listTest(Long appId) {
        String keycloakId = userIdentity.getKeycloakId();
        return testRunBO.findByAppId(appId, keycloakId);
    }

    @Override
    public TestRunDTO retrieveTest(Long appId, Long id) {
        return testRunBO.getById(id, userIdentity.getKeycloakId());
    }

    @Override
    public List<RunMessageDTO> listTestMessages(Long appId, Long id) {
        return testRunMessageBO.findByRun(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public List<RunMessageLogDTO> listTestLogMessages(Long appId, Long id) {
        return testRunMessageBO.findLogByRun(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public List<RunMessageMetricDTO> listTestMetricMessages(Long appId, Long id) {
        return testRunMessageBO.findMetricByRun(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public List<FederatedTestRunDTO> listFederatedRuns(Long appId) {
        return federatedTestRunBO.findByAppId(appId, userIdentity.getKeycloakId());
    }

    @Override
    public FederatedTestRunDTO retrieveFederatedRun(Long appId, Long id) {
        return federatedTestRunBO.getByIdAuth(appId, id, userIdentity.getKeycloakId());
    }

    @Override
    public List<FederatedParticipantDTO> listFederatedParticipants(Long appId, Long id) {
        // auth check implicit via getByIdAuth
        federatedTestRunBO.getByIdAuth(appId, id, userIdentity.getKeycloakId());
        return FederatedParticipantBO.listParticipants(id);
    }

    @Override
    public List<FederatedRoundMessageDTO> listFederatedRoundMessages(Long appId, Long id) {
        federatedTestRunBO.getByIdAuth(appId, id, userIdentity.getKeycloakId());
        return federatedRoundMessageBO.listRoundMessages(id);
    }

    @Override
    public List<RunMessageDTO> listFederatedRunMessages(Long appId, Long runId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return federatedParticipantMessageBO.findByParticipant(appId, id, keycloakId);
    }

    @Override
    public List<RunMessageLogDTO> listFederatedRunLogMessages(Long appId, Long runId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return federatedParticipantMessageBO.findLogByParticipant(appId, id, keycloakId);
    }

    @Override
    public List<RunMessageMetricDTO> listFederatedRunMetricMessages(Long appId, Long runId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return federatedParticipantMessageBO.findMetricByParticipant(appId, id, keycloakId);
    }

    @Override
    public List<ExperimentDTO> listExperiment(Long appId) {
        String keycloakId = userIdentity.getKeycloakId();
        return experimentBO.findByAppId(appId, keycloakId);
    }

    @Override
    public ExperimentDetailDTO retrieveExperiment(Long appId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return experimentBO.getById(appId, id, keycloakId);
    }

    @Override
    @Transactional
    public ExperimentDetailDTO updateExperiment(Long appId, Long id, ExperimentDetailDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        return experimentBO.update(dto, appId, keycloakId);
    }

    @Override
    @Transactional
    public Response createExperiment(Long appId, CreateExperimentDTO createdto) {
        String keycloakId = userIdentity.getKeycloakId();
        ExperimentDetailDTO dto = experimentBO.createExperiment(createdto, appId, keycloakId);
        return Response.status(Response.Status.CREATED)  // 201 status
                .entity(dto)
                .build();
    }

    @Override
    public List<ExperimentRunDTO> listExperimentRuns(Long appId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return experimentBO.listExperimentRuns(id, keycloakId);
    }

    @Override
    public List<RunMessageDTO> listExperimentRunMessages(Long appId, Long experimentId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return experimentRunMessageBO.findByRun(appId, id, keycloakId);
    }

    @Override
    public List<RunMessageLogDTO> listExperimentRunLogMessages(Long appId, Long experimentId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return experimentRunMessageBO.findLogByRun(appId, id, keycloakId);
    }

    @Override
    public List<RunMessageMetricDTO> listExperimentRunMetricMessages(Long appId, Long experimentId, Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return experimentRunMessageBO.findMetricByRun(appId, id, keycloakId);
    }

    @Override
    public List<RunMessageMetricDTO> listExperimentMetricMessages(Long appId, Long experimentId) {
        String keycloakId = userIdentity.getKeycloakId();
        return experimentRunMessageBO.findMetricByExperiment(appId, experimentId, keycloakId);
    }
}
