package bio.cosy.feddb.local.api.importer.run.patientlog;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ConnectorRunPatientLogAO implements PanacheRepository<ConnectorRunPatientLogEntity> {

    public List<ConnectorRunPatientLogEntity> findByRunId(Long runId) {
        return list("run.id", runId);
    }

    public List<ConnectorRunPatientLogEntity> findByRunIdAndType(Long runId, ConnectorRunPatientLogType logType) {
        return list("run.id = ?1 and logType = ?2", runId, logType);
    }

    public List<ConnectorRunPatientLogEntity> findByRunIdWithPatient(Long runId) {
        return list("run.id = ?1 and patientId is not null and patientId != 'Unknown'", runId);
    }

    public List<ConnectorRunPatientLogEntity> findByRunIdWithoutPatient(Long runId) {
        return list("run.id = ?1 and (patientId is null or patientId = 'Unknown')", runId);
    }

    public List<ConnectorRunPatientLogEntity> findByRunIdAndTypeWithPatient(Long runId, ConnectorRunPatientLogType logType) {
        return list("run.id = ?1 and logType = ?2 and patientId is not null and patientId != 'Unknown'",
                runId, logType);
    }

    public List<ConnectorRunPatientLogEntity> findByRunIdAndTypeWithoutPatient(Long runId, ConnectorRunPatientLogType logType) {
        return list("run.id = ?1 and logType = ?2 and (patientId is null or patientId = 'Unknown')",
                runId, logType);
    }
}
