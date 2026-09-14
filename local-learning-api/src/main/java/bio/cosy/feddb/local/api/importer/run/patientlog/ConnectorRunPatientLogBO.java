package bio.cosy.feddb.local.api.importer.run.patientlog;

import bio.cosy.feddb.core.base.BaseBo;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ConnectorRunPatientLogBO extends BaseBo<ConnectorRunPatientLogDTO, ConnectorRunPatientLogEntity, ConnectorRunPatientLogAO, ConnectorRunPatientLogMapper> {

    @Transactional
    public void createLog(String message, Long runId, ConnectorRunPatientLogType logType) {
        createLog(message, runId, logType, "ERROR", null, null);
    }

    @Transactional
    public void createLog(String message, Long runId, ConnectorRunPatientLogType logType, String level,
                          String patientId, String field) {
        Log.debugf("RUN: %d -> log_type: %s -> LOG: %s", runId, logType, message);

        ConnectorRunPatientLogDTO dto = new ConnectorRunPatientLogDTO();
        dto.setMessage(message);
        dto.setRunId(runId);
        dto.setLogType(logType);
        dto.setLevel(level != null ? level : "ERROR");
        dto.setPatientId(patientId);
        dto.setField(field);
        create(dto);
    }

    public void createPatientLog(String message, Long runId, String patientId, String field) {
        createLog(message, runId, ConnectorRunPatientLogType.MAPPING, "ERROR", patientId, field);
    }

    public List<ConnectorRunPatientLogDTO> getByRunId(Long runId, String logType, String patientFilter) {
        ConnectorRunPatientLogType type = parseLogType(logType);
        List<ConnectorRunPatientLogEntity> entities;

        if (type != null && patientFilter != null) {
            entities = "yes".equalsIgnoreCase(patientFilter)
                    ? ao.findByRunIdAndTypeWithPatient(runId, type)
                    : ao.findByRunIdAndTypeWithoutPatient(runId, type);
        } else if (type != null) {
            entities = ao.findByRunIdAndType(runId, type);
        } else if (patientFilter != null) {
            entities = "yes".equalsIgnoreCase(patientFilter)
                    ? ao.findByRunIdWithPatient(runId)
                    : ao.findByRunIdWithoutPatient(runId);
        } else {
            entities = ao.findByRunId(runId);
        }

        return mapper.entitiesToDtos(entities);
    }

    private ConnectorRunPatientLogType parseLogType(String logType) {
        if (logType == null || logType.isBlank()) {
            return null;
        }
        try {
            return ConnectorRunPatientLogType.valueOf(logType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
