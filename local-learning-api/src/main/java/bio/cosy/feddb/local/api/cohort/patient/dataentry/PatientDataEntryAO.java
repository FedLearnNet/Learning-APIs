package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.local.api.helper.IdChunks;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class PatientDataEntryAO implements PanacheRepository<PatientDataEntryEntity> {

    /** PostgreSQL rejects a prepared statement with more than 65535 bind parameters. */
    private static final int MAX_QUERY_PARAMETERS = 65535;

    public void deleteAllByCohortAndPatient(Long cohortId, Long internalPatientId) {
        delete("patient.cohort.id = ?1 and patient.id = ?2", cohortId, internalPatientId);
    }

    public void deleteAllByPatient(Long internalPatientId) {
        delete("patient.id = ?1", internalPatientId);
    }

    public List<PatientDataEntryEntity> findByCohortAndPatient(Long cohortId, Long internalPatientId) {
        return find("patient.cohort.id = ?1 and patient.id = ?2", cohortId, internalPatientId).list();
    }

    public List<PatientDataEntryEntity> findByCohortAndPatientIds(Long cohortId, List<Long> internalPatientIds) {
        return IdChunks.collect(internalPatientIds,
                chunk -> find("patient.cohort.id = ?1 and patient.id IN ?2", cohortId, chunk).list());
    }

    public List<PatientDataEntryEntity> findByPatientIds(List<Long> internalPatientIds) {
        return IdChunks.collect(internalPatientIds, chunk -> find("patient.id IN ?1", chunk).list());
    }

    public List<PatientDataEntryEntity> findReducedPageEntries(Long cohortId, List<Long> internalPatientIds) {
        if (internalPatientIds == null || internalPatientIds.isEmpty()) {
            return List.of();
        }

        return IdChunks.collect(internalPatientIds, chunk -> find("""
                        SELECT de
                        FROM PatientDataEntryEntity de
                        JOIN FETCH de.patient p
                        JOIN FETCH de.schemaNode sn
                        LEFT JOIN FETCH sn.dataType
                        WHERE p.cohort.id = ?1
                          AND p.id IN ?2
                        ORDER BY p.id, de.id
                        """, cohortId, chunk)
                .list());
    }

    public void deleteBySchemaNode(
            PatientEntity patient,
            SchemaNodeEntity schemaNode,
            String visitId,
            Instant visitTimestamp) {

        StringBuilder query = new StringBuilder("""
            patient = ?1
            and schemaNode = ?2
        """);

        List<Object> params = new ArrayList<>();
        params.add(patient);
        params.add(schemaNode);

        int index = 3;

        if (visitId == null) {
            query.append(" and visitId is null");
        } else {
            query.append(" and visitId = ?").append(index++);
            params.add(visitId);
        }

        if (visitTimestamp == null) {
            query.append(" and visitTimestamp is null");
        } else {
            query.append(" and visitTimestamp = ?").append(index);
            params.add(visitTimestamp);
        }

        delete(query.toString(), params.toArray());
    }


    public PatientDataEntryEntity mergeDataEntries(PatientDataEntryEntity entry) {
        Long id = entry.getId();
        Long version = entry.getVersion();
        Optional<PatientDataEntryEntity> foundEntityOptional = findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
        if (foundEntityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Patient data entry ID %s not found", id));
        }
        PatientDataEntryEntity foundEntity = foundEntityOptional.get();

        if (version == null) {
            version = 0L; // Default version if not provided
        }
        if (!version.equals(foundEntity.getVersion())) {
            throw new ClientErrorException("The entity was updated by another transaction", Response.Status.FORBIDDEN);
        }

        if (entry.getCreatedAt() != null && !entry.getCreatedAt().equals(foundEntity.getCreatedAt())) {
            throw new ClientErrorException("The createdAt timestamp cannot be changed", Response.Status.FORBIDDEN);
        }

        // entry.setUpdatedAt(new Date());
        //TODO CHECK AND FIX LATER; SHOUDNT BE NEEDED
        // entry.setVersion(entry.getVersion() + 1);
        entry = getEntityManager().merge(entry);
        flush();
        return entry;
    }

    public List<PatientDataEntryEntity> findByIdsAndDataIds(List<Long> patientIds, List<SelectedDataIdsDTO> selectedData) {
        Map<String, Object> dataFilterParams = new HashMap<>();
        int count = 0;

        StringBuilder queryBuilder = new StringBuilder("patient.id in :ids");

        if (selectedData != null && !selectedData.isEmpty()) {
            queryBuilder.append(" AND ( ");
            List<String> filters = new ArrayList<>();
            for (SelectedDataIdsDTO entry : selectedData) {
                String ontoParam = "onto" + count;
                String typeParam = "type" + count;

                String filter = " ( schemaNode.dataType.globalDataTypeId = :" + typeParam
                        + " AND schemaNode.ontology.globalId = :" + ontoParam + " )";
                dataFilterParams.put(ontoParam, entry.getGlobalOntologyId());
                dataFilterParams.put(typeParam, entry.getGlobalDataTypeId());
                filters.add(filter);
                count++;
            }

            queryBuilder.append(String.join(" OR ", filters));
            queryBuilder.append(" )");
        }
        String query = queryBuilder.toString();
        Log.debugf("Executing query: " + query + " with params: " + dataFilterParams);

        //Executing query: id in :ids AND (  ( schemaNode.dataType.globalDataTypeId = :type0 AND
        // schemaNode.ontology.globalId = :onto0 ) OR  ( schemaNode.dataType.globalDataTypeId = :type1
        // AND schemaNode.ontology.globalId = :onto1 ) )
        // with params: {onto1=C0239081, ids=[9], onto0=C0920688, type1=Yes_No:boolean, type0=Yes_No:boolean}

        // The data id filters already bind two parameters each, so the id chunks have to be sized
        // around them to stay below the PostgreSQL limit of 65535 bind parameters per statement.
        int idsPerChunk = idChunkSize(dataFilterParams.size());
        return IdChunks.collect(patientIds, idsPerChunk, chunk -> {
            Map<String, Object> params = new HashMap<>(dataFilterParams);
            params.put("ids", chunk);
            return find(query, params).list();
        });
    }

    private static int idChunkSize(int reservedParameters) {
        int available = MAX_QUERY_PARAMETERS - reservedParameters;
        if (available < 1) {
            throw new IllegalArgumentException(
                    "Too many selected data ids for a single query: " + reservedParameters + " parameters");
        }
        return Math.min(IdChunks.MAX_IN_CLAUSE_IDS, available);
    }

}
