package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesDTO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunRunMessagesBO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ConnectorRunServiceImpl implements ConnectorRunService {

    @Inject
    ConnectorRunBO runBO;

    @Inject
    ConnectorRunPatientLogBO errorLogBO;

    @Inject
    ConnectorRunRunMessagesBO messagesBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public List<ConnectorRunDTO> listRuns() {
        Set<Long> cohortIds = cohortMemberAuthBO.memberCohortIdsOf(userIdentity.getKeycloakId());
        return runBO.getAll().stream()
                .filter(run -> cohortIds.contains(run.getCohortId()))
                .toList();
    }

    @Override
    public ConnectorRunDTO getRun(Long id) {
        return checkedRun(id);
    }

    @Override
    public List<ConnectorRunDTO> getAllRunsForConnector(Long connectorId) {
        cohortMemberAuthBO.checkForMember(connectorBO.getById(connectorId).getCohortId(), userIdentity.getKeycloakId());
        return runBO.getAllForConnector(connectorId);
    }

    @Override
    public List<ConnectorRunMessagesDTO> getLogs(Long id) {
        checkedRun(id);
        return messagesBO.findByRunId(id);
    }

    @Override
    public RunErrorLogListResponseDTO getRunLogs(Long id, String type, String patient) {
        checkedRun(id);
        RunErrorLogListResponseDTO response = new RunErrorLogListResponseDTO();
        response.setLogs(errorLogBO.getByRunId(id, type, patient));
        return response;
    }

    private ConnectorRunDTO checkedRun(Long id) {
        ConnectorRunDTO run = runBO.getById(id);
        cohortMemberAuthBO.checkForMember(run.getCohortId(), userIdentity.getKeycloakId());
        return run;
    }
}
