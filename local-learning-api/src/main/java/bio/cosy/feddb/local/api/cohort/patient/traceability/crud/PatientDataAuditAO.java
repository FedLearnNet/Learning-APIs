package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.logging.Log;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;

import java.util.List;

@ApplicationScoped
public class PatientDataAuditAO {

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<PatientDataEntryEntity> getDataEntriesAtRevision(Long patientId, Integer revisionNumber) {
        var auditReader = AuditReaderFactory.get(em);
        return auditReader.createQuery()
                .forEntitiesAtRevision(PatientDataEntryEntity.class, revisionNumber)
                .add(AuditEntity.property("patient_id").eq(patientId))
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public Integer findPreviousRevision(Long patientId, Integer beforeRevisionId) {
        var auditReader = AuditReaderFactory.get(em);
        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(PatientEntity.class, false, true)
                .add(AuditEntity.id().eq(patientId))
                .add(AuditEntity.revisionNumber().lt(beforeRevisionId))
                .addOrder(AuditEntity.revisionNumber().desc())
                .setMaxResults(1)
                .getResultList();

        if (results.isEmpty()) {
            return null;
        }
        CustomRevisionEntity rev = (CustomRevisionEntity) results.getFirst()[1];
        return rev.getId();
    }

    public void deleteAuditEntriesFrom(Long patientId, Integer fromRevisionNumber) {
        int metaDataDeleted = em.createNativeQuery(
                        "DELETE FROM meta_patient_data_entry_audit WHERE patient_data_entry_id IN " +
                                "(SELECT id FROM patient_data_audit WHERE patient_id = :pid AND rev >= :rev) AND rev >= :rev")
                .setParameter("pid", patientId)
                .setParameter("rev", fromRevisionNumber)
                .executeUpdate();

        int dataDeleted = em.createNativeQuery(
                        "DELETE FROM patient_data_audit WHERE patient_id = :pid AND rev >= :rev")
                .setParameter("pid", patientId)
                .setParameter("rev", fromRevisionNumber)
                .executeUpdate();

        int patientDeleted = em.createNativeQuery(
                        "DELETE FROM patient_meta_audit WHERE id = :pid AND rev >= :rev")
                .setParameter("pid", patientId)
                .setParameter("rev", fromRevisionNumber)
                .executeUpdate();

        Log.infof("Deleted audit entries for patient %d from revision %d: %d patient_meta, %d patient_data, %d meta_data_entry",
                patientId, fromRevisionNumber, patientDeleted, dataDeleted, metaDataDeleted);
    }

    public int deleteAllAuditEntriesForCohort(Long cohortId) {
        int metaDataDeleted = em.createNativeQuery(
                        "DELETE FROM meta_patient_data_entry_audit WHERE patient_data_entry_id IN " +
                                "(SELECT pda.id FROM patient_data_audit pda " +
                                "INNER JOIN patient_meta pm ON pm.id = pda.patient_id " +
                                "WHERE pm.cohort_id = :cohortId)")
                .setParameter("cohortId", cohortId)
                .executeUpdate();

        int dataDeleted = em.createNativeQuery(
                        "DELETE FROM patient_data_audit WHERE patient_id IN " +
                                "(SELECT id FROM patient_meta WHERE cohort_id = :cohortId)")
                .setParameter("cohortId", cohortId)
                .executeUpdate();

        int patientDeleted = em.createNativeQuery(
                        "DELETE FROM patient_meta_audit WHERE cohort_id = :cohortId")
                .setParameter("cohortId", cohortId)
                .executeUpdate();

        Log.infof("Deleted all audit entries for cohort %d: %d patient_meta, %d patient_data, %d meta_data_entry",
                cohortId, patientDeleted, dataDeleted, metaDataDeleted);
        return patientDeleted + dataDeleted + metaDataDeleted;
    }

    public int deleteAllAuditEntriesForPatients(List<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return 0;
        }

        int metaDataDeleted = em.createNativeQuery(
                        "DELETE FROM meta_patient_data_entry_audit WHERE patient_data_entry_id IN " +
                                "(SELECT id FROM patient_data_audit WHERE patient_id IN (:patientIds))")
                .setParameter("patientIds", patientIds)
                .executeUpdate();

        int dataDeleted = em.createNativeQuery(
                        "DELETE FROM patient_data_audit WHERE patient_id IN (:patientIds)")
                .setParameter("patientIds", patientIds)
                .executeUpdate();

        int patientDeleted = em.createNativeQuery(
                        "DELETE FROM patient_meta_audit WHERE id IN (:patientIds)")
                .setParameter("patientIds", patientIds)
                .executeUpdate();

        Log.infof("Deleted audit entries for %d patients: %d patient_meta, %d patient_data, %d meta_data_entry",
                patientIds.size(), patientDeleted, dataDeleted, metaDataDeleted);
        return patientDeleted + dataDeleted + metaDataDeleted;
    }

    public void deleteAllAuditEntries(Long patientId) {
        int metaDataDeleted = em.createNativeQuery(
                        "DELETE FROM meta_patient_data_entry_audit WHERE patient_data_entry_id IN " +
                                "(SELECT id FROM patient_data_audit WHERE patient_id = :pid)")
                .setParameter("pid", patientId)
                .executeUpdate();

        int dataDeleted = em.createNativeQuery(
                        "DELETE FROM patient_data_audit WHERE patient_id = :pid")
                .setParameter("pid", patientId)
                .executeUpdate();

        int patientDeleted = em.createNativeQuery(
                        "DELETE FROM patient_meta_audit WHERE id = :pid")
                .setParameter("pid", patientId)
                .executeUpdate();

        Log.infof("Deleted all audit entries for patient %d: %d patient_meta, %d patient_data, %d meta_data_entry",
                patientId, patientDeleted, dataDeleted, metaDataDeleted);
    }

    public void deleteAuditEntriesForRun(Long patientId, Long runId) {
        int metaDataDeleted = em.createNativeQuery(
                        "DELETE FROM meta_patient_data_entry_audit WHERE patient_data_entry_id IN " +
                                "(SELECT id FROM patient_data_audit WHERE patient_id = :pid " +
                                "AND rev IN (SELECT id FROM revinfo WHERE run_id = :runId)) " +
                                "AND rev IN (SELECT id FROM revinfo WHERE run_id = :runId)")
                .setParameter("pid", patientId)
                .setParameter("runId", runId)
                .executeUpdate();

        int dataDeleted = em.createNativeQuery(
                        "DELETE FROM patient_data_audit WHERE patient_id = :pid " +
                                "AND rev IN (SELECT id FROM revinfo WHERE run_id = :runId)")
                .setParameter("pid", patientId)
                .setParameter("runId", runId)
                .executeUpdate();

        int patientDeleted = em.createNativeQuery(
                        "DELETE FROM patient_meta_audit WHERE id = :pid " +
                                "AND rev IN (SELECT id FROM revinfo WHERE run_id = :runId)")
                .setParameter("pid", patientId)
                .setParameter("runId", runId)
                .executeUpdate();

        Log.infof("Deleted audit entries for patient %d, run %d: %d patient_meta, %d patient_data, %d meta_data_entry",
                patientId, runId, patientDeleted, dataDeleted, metaDataDeleted);
    }

    public int getMaxRevisionNumber() {
        Object result = em.createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM revinfo")
                .getSingleResult();
        return ((Number) result).intValue();
    }
}
