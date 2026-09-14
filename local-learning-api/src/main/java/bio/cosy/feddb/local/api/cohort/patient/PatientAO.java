package bio.cosy.feddb.local.api.cohort.patient;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Date;

import bio.cosy.feddb.local.api.helper.IdChunks;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryAO;

@ApplicationScoped
public class PatientAO implements PanacheRepository<PatientEntity> {

    @Inject
    PatientDataEntryAO patientDataEntryAO;

    public List<PatientEntity> findAllByCohortId(Long cohortId) {
        // Returns all PatientMetaEntities for a given cohort
        return find("cohort.id", cohortId).list();
    }

    public long deleteAllPatients(Long cohortId) {
        return delete("cohort.id", cohortId);
    }

    /**
     * Patient ids of a cohort as a projection: a cohort export only needs the ids, and loading
     * every patient as a managed entity just to read one column fills the persistence context.
     */
    public List<Long> findIdsByCohortId(Long cohortId) {
        return findIdsByCohortId(cohortId, null, null);
    }

    /**
     * Patient ids of a cohort, narrowed by an optional set of external patient ids and an optional
     * maximum. Ids are ordered by internal patient id so that a limit selects a stable subset.
     */
    public List<Long> findIdsByCohortId(Long cohortId, Collection<String> externalPatientIds, Integer limit) {
        if (externalPatientIds != null && externalPatientIds.isEmpty()) {
            return List.of();
        }

        if (externalPatientIds == null) {
            var query = getEntityManager()
                    .createQuery("SELECT p.id FROM PatientEntity p WHERE p.cohort.id = :cohortId ORDER BY p.id", Long.class)
                    .setParameter("cohortId", cohortId);
            if (limit != null) {
                query.setMaxResults(limit);
            }
            return query.getResultList();
        }

        List<Long> ids = IdChunks.collect(externalPatientIds, chunk -> getEntityManager()
                .createQuery("SELECT p.id FROM PatientEntity p WHERE p.cohort.id = :cohortId"
                        + " AND p.externalPatientId IN :externalIds ORDER BY p.id", Long.class)
                .setParameter("cohortId", cohortId)
                .setParameter("externalIds", chunk)
                .getResultList());
        ids.sort(Long::compareTo);
        return limit != null && limit < ids.size() ? List.copyOf(ids.subList(0, limit)) : ids;
    }

    /**
     * Patient ids and external ids of a cohort, capped at {@code limit}. A projection, because a
     * picker only needs the two identifying columns and a cohort can hold hundreds of thousands
     * of patients.
     */
    public List<PatientReferenceDTO> findReferencesByCohortId(Long cohortId, int limit) {
        return getEntityManager()
                .createQuery("SELECT new bio.cosy.feddb.local.api.cohort.patient.PatientReferenceDTO(p.id, p.externalPatientId)"
                        + " FROM PatientEntity p WHERE p.cohort.id = :cohortId ORDER BY p.id", PatientReferenceDTO.class)
                .setParameter("cohortId", cohortId)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Long> findIdsByCohortId(Long cohortId, int page, int pageSize) {
        return getEntityManager()
                .createQuery("SELECT p.id FROM PatientEntity p WHERE p.cohort.id = :cohortId ORDER BY p.id", Long.class)
                .setParameter("cohortId", cohortId)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public void deleteByIds(List<Long> patientIds) {
        IdChunks.forEach(patientIds, chunk -> delete("id in ?1", chunk));
    }

    public List<PatientEntity> findAllByIds(List<Long> patientIds) {
        return IdChunks.collect(patientIds, chunk -> find("id in ?1", chunk).list());
    }

    public List<PatientEntity> findAllByInternalIds(Long cohortId, Collection<Long> patientIds) {
        return IdChunks.collect(patientIds, chunk -> find("cohort.id = ?1 and id in ?2", cohortId, chunk).list());
    }

    /**
     * Cohort id per patient id for the given patients, as a projection: a cohort-wide query result
     * can name hundreds of thousands of patients, and loading them as managed entities just to read
     * two columns fills the persistence context for no benefit.
     */
    public Map<Long, List<Long>> groupIdsByCohortId(Collection<Long> patientIds) {
        Map<Long, List<Long>> grouped = new LinkedHashMap<>();
        IdChunks.forEach(patientIds, chunk -> {
            List<Object[]> rows = getEntityManager()
                    .createQuery("SELECT p.cohort.id, p.id FROM PatientEntity p WHERE p.id IN :ids", Object[].class)
                    .setParameter("ids", chunk)
                    .getResultList();
            for (Object[] row : rows) {
                grouped.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>()).add((Long) row[1]);
            }
        });
        return grouped;
    }

    public List<PatientEntity> findEffectedPatientsByQueryId(Long queryId) {
        return find("SELECT DISTINCT q.patient FROM QueryPatientEntity q WHERE q.query.id = ?1", queryId).list();
    }

    public List<PatientEntity> findAllByLearningRequestId(Long requestId) {
        return find("SELECT DISTINCT pat.patient FROM PatientLearningEntity pat WHERE pat.request.id = ?1", requestId).list();
    }

    public List<PatientEntity> findAllByCohortId(Long cohortId, int page, int pageSize) {
        // Returns paginated PatientMetaEntities for a given cohort
        // Page is 0-based for Panache, but we'll convert from 1-based in the BO
        return find("cohort.id", cohortId)
            .page(page, pageSize)
            .list();
    }

    public long countByCohortId(Long cohortId) {
        // Returns the total count of patients in a cohort for pagination metadata
        return count("cohort.id", cohortId);
    }

    public Optional<PatientEntity> findByInternalId(Long cohortId, Long id) {
        return find("cohort.id = ?1 and id = ?2", cohortId, id).firstResultOptional();
    }

    public int setLastQueriedAt(Collection<Long> patientIds, Long cohortId, LocalDateTime lastQueriedAt) {
        if (patientIds == null || patientIds.isEmpty()) {
            return 0;
        }
        return update("lastQueriedAt = ?1 where cohort.id = ?2 and id in ?3", lastQueriedAt, cohortId, patientIds);
    }

    public Optional<PatientEntity> findByExternalPatientId(Long cohortId, String externalPatientId) {
        return find("cohort.id = ?1 and externalPatientId = ?2", cohortId, externalPatientId).firstResultOptional();
    }

    public void increaseDataEntriesVersion(Long id){
        // Increases the dataEntryVersion of the patient entity by 1
        // This is used to track changes in the patient data
        findByIdOptional(id).ifPresent(patient -> {
            patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
            patient.setUpdatedAt(new Date());
            persist(patient);
        });
        // the version attribute is automatically updated due to the @Version annotation
    }

    public void deleteByInternalId(Long cohortId, Long id) {
        // Only deletes the PatientMetaEntity.patientDataEntities
        // throws BadRequestException if the patient to delete does not exist
        findByInternalId(cohortId, id).ifPresentOrElse(patient -> {
            patient.getDataEntries().forEach(patientDataEntryAO::delete);
        }, () -> {
            throw new BadRequestException("Patient with id " + id + " not found");
        });
    }

    public void hardDeleteByInternalId(Long cohortId, Long id) {
        findByInternalId(cohortId, id).ifPresentOrElse(this::delete, () -> {
            throw new BadRequestException("Patient with id " + id + " not found");
        });
    }
}
