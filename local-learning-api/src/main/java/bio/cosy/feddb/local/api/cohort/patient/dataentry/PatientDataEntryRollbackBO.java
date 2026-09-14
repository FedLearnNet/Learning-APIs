package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.AuditAO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.AuditFieldEnum;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.PatientDataAuditAO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.TracabilityLog;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PatientDataEntryRollbackBO {

    @Inject
    EntityManager entityManager;

    @Inject
    PatientAO patientAO;

    @Inject
    PatientDataEntryAO dataEntryAO;

    @Inject
    AuditAO auditAO;

    @Inject
    PatientDataAuditAO patientDataAuditAO;

    @Inject
    PatientDataMapper patientDataMapper;

    public void rollbackPatientToRevision(Long patientId, Integer revisionNumber, boolean deleteAudit) {
        PatientEntity patient = patientAO.findByIdOptional(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + patientId));

        List<PatientDataEntryEntity> snapshotEntries = patientDataAuditAO.getDataEntriesAtRevision(patientId, revisionNumber);
        Log.infof("Rolling back patient %d to revision %d (%d data entries)", patientId, revisionNumber, snapshotEntries.size());

        // Remember max revision before rollback so we can clean up the audit entries
        // that Envers creates for the rollback operations themselves
        int maxRevBefore = patientDataAuditAO.getMaxRevisionNumber();

        patient.getDataEntries().clear();
        entityManager.flush();

        for (PatientDataEntryEntity audited : snapshotEntries) {
            PatientDataEntryEntity restored = patientDataMapper.entityCopy(audited);
            restored.setPatient(patient);
            dataEntryAO.persist(restored);
            patient.getDataEntries().add(restored);
        }

        patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
        patientAO.persist(patient);
        entityManager.flush();

        // Delete audit entries created by the rollback itself — rollback should not leave an audit trail
        patientDataAuditAO.deleteAuditEntriesFrom(patientId, maxRevBefore + 1);

        if (deleteAudit) {
            patientDataAuditAO.deleteAuditEntriesFrom(patientId, revisionNumber);
        } else {
            Log.warnf("Rollback of patient %d to revision %d completed WITHOUT deleting audit history. " +
                    "Audit entries at and after revision %d still exist.", patientId, revisionNumber, revisionNumber);
        }

        Log.infof("Rollback complete for patient %d, restored %d entries", patientId, snapshotEntries.size());
    }

    public void rollbackConnectorRun(Long runId, boolean deleteAudit) {
        List<TracabilityLog> affectedRevisions = auditAO.queryAuditChanges(runId.toString(), AuditFieldEnum.RUN_ID);

        if (affectedRevisions.isEmpty()) {
            Log.infof("No patient changes found for run %d, nothing to rollback", runId);
            return;
        }

        Set<Long> processedPatients = new HashSet<>();
        for (TracabilityLog log : affectedRevisions) {
            Long patientId = log.getInternalPatientId();
            if (!processedPatients.add(patientId)) {
                continue;
            }

            Integer runRevisionId = log.getRevisionId();
            Integer previousRevision = patientDataAuditAO.findPreviousRevision(patientId, runRevisionId);

            if (previousRevision != null) {
                rollbackPatientToRevision(patientId, previousRevision, deleteAudit);
            } else {
                clearPatientData(patientId);
                if (deleteAudit) {
                    patientDataAuditAO.deleteAuditEntriesForRun(patientId, runId);
                } else {
                    Log.warnf("Cleared data for patient %d but audit history was kept.", patientId);
                }
            }
        }
        Log.infof("Rollback of run %d complete, %d patients affected", runId, processedPatients.size());
    }

    private void clearPatientData(Long patientId) {
        PatientEntity patient = patientAO.findByIdOptional(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found: " + patientId));

        int maxRevBefore = patientDataAuditAO.getMaxRevisionNumber();

        patient.getDataEntries().clear();
        patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
        patientAO.persist(patient);
        entityManager.flush();

        // Delete audit entries created by the clear operation itself
        patientDataAuditAO.deleteAuditEntriesFrom(patientId, maxRevBefore + 1);

        Log.infof("Cleared all data entries for patient %d (no prior revision exists)", patientId);
    }
}
