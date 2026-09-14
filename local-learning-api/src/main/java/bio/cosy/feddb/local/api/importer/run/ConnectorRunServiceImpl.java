package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesDTO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunRunMessagesBO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ConnectorRunServiceImpl implements ConnectorRunService {

    @Inject
    ConnectorRunBO runBO;

    @Inject
    ConnectorRunPatientLogBO errorLogBO;

    @Inject
    ConnectorRunStepBO stepBO;

    @Inject
    ConnectorRunRunMessagesBO messagesBO;

    @Override
    public List<ConnectorRunDTO> listRuns() {
        return runBO.getAll();
    }

    @Override
    public ConnectorRunDTO getRun(Long id) {
        return runBO.getById(id);
    }

    @Override
    public List<ConnectorRunDTO> getAllRunsForConnector(Long connectorId) {
        return runBO.getAllForConnector(connectorId);
    }

    @Override
    public List<ConnectorRunMessagesDTO> getLogs(Long id) {
        return messagesBO.findByRunId(id);
    }

    @Override
    public RunErrorLogListResponseDTO getRunLogs(Long id, String type, String patient) {
        RunErrorLogListResponseDTO response = new RunErrorLogListResponseDTO();
        response.setLogs(errorLogBO.getByRunId(id, type, patient));
        return response;
    }
}
