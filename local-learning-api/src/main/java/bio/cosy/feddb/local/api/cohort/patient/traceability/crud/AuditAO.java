package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import com.github.dockerjava.api.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AuditAO {

    @Inject
    EntityManager entityManager;

    /**
     * Main query method for audit entries with full flexibility.
     * This method supports multiple search criteria, pagination, and custom sorting.
     *
     * @param searchCriteria List of search criteria to apply (all must match - AND operation)
     * @param firstResult    The first result index (0-based), null for no pagination
     * @param maxResults     The maximum number of results to return, null for no limit
     * @param sortCriteria   Sorting criteria, null for default sorting (revision timestamp desc)
     * @return List of TracabilityLogDTO with audit information including change date and type
     */
    public List<TracabilityLog> queryAuditChanges(List<AuditSearchCriteriaDTO> searchCriteria,
                                                  Integer firstResult, Integer maxResults,
                                                  AuditSortCriteriaDTO sortCriteria) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(PatientEntity.class, false, true);

        // Apply all search criteria
        if (searchCriteria != null) {
            for (AuditSearchCriteriaDTO criteria : searchCriteria) {
                addSearchConstraint(query, criteria.getSearchTerm(), criteria.getSearchType());
            }
        }

        // Apply sorting
        addSorting(query, sortCriteria);

        // Apply pagination if specified
        if (firstResult != null) {
            query.setFirstResult(firstResult);
        }
        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        List<Object[]> results = executeAuditQuery(query);

        return convertToTracabilityLogDTOs(results);
    }

    public long countAuditChanges(List<AuditSearchCriteriaDTO> searchCriteria) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(PatientEntity.class, false, true);

        if (searchCriteria != null) {
            for (AuditSearchCriteriaDTO criteria : searchCriteria) {
                addSearchConstraint(query, criteria.getSearchTerm(), criteria.getSearchType());
            }
        }

        query.addProjection(AuditEntity.id().count());
        Object result = query.getSingleResult();
        if (result == null) {
            return 0L;
        }

        return ((Number) result).longValue();
    }

    public TracabilityLog getPatientChangesForRev(Long patientId, Integer revId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(PatientEntity.class, false, true);

        query.add(AuditEntity.revisionNumber().eq(revId));
        query.add(AuditEntity.id().eq(patientId));
        List<Object[]> results;
        try {
            results = executeAuditQuery(query);
            if (results == null) {
                throw new NotFoundException("No patient with id " + patientId + " found");
            }
        } catch (NoResultException e) {
            throw new NotFoundException("No patient with id " + patientId + " found");
        }
        List<TracabilityLog> mapped = convertToTracabilityLogDTOs(results);
        if (mapped.isEmpty()) {
            throw new NotFoundException("No patient with id " + patientId + " found");
        }
        if (mapped.size() > 1) {
            throw new IllegalStateException("Multiple revisions found for patient " + patientId + " with revId " + revId);
        }
        return mapped.getFirst();
    }

    public List<TracabilityDataChangeLog> getPatientDataChangesForRev(Long patientId, Integer revId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(PatientDataEntryEntity.class, false, true);

        query.add(AuditEntity.revisionNumber().eq(revId));
        query.add(AuditEntity.property("patient_id").eq(patientId));
        List<Object[]> results = executeAuditQuery(query);
        return convertToTracabilityDataChangeLogs(results);
    }

    @SuppressWarnings("unchecked")
    public CustomRevisionEntity getRevForDataEntriesVersion(Long patientId, Long dataEntriesVersion) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(PatientEntity.class, false, true);

        query.add(AuditEntity.id().eq(patientId));
        query.add(AuditEntity.property("dataEntriesVersion").eq(dataEntriesVersion));
        List<Object[]> results = query.getResultList();

        if (!results.isEmpty()) {
            return (CustomRevisionEntity) results.getFirst()[1];
        }

        return null;
    }


    public List<TracabilityLog> queryAuditChanges(String searchTerm, AuditFieldEnum searchType) {
        List<AuditSearchCriteriaDTO> searchCriteria = List.of(new AuditSearchCriteriaDTO(searchTerm, searchType));
        return queryAuditChanges(searchCriteria, null, null, null);
    }


    public List<TracabilityLog> queryAuditChanges(String searchTerm, AuditFieldEnum searchType,
                                                  int firstResult, int maxResults) {
        List<AuditSearchCriteriaDTO> searchCriteria = List.of(new AuditSearchCriteriaDTO(searchTerm, searchType));
        return queryAuditChanges(searchCriteria, firstResult, maxResults, null);
    }

    /**
     * Utility method to parse comma-separated search terms and fields into SearchCriteria list.
     *
     * @param searchTerms  Comma-separated search terms
     * @param searchFields Comma-separated search field names
     * @return List of SearchCriteria
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public List<AuditSearchCriteriaDTO> parseSearchCriteria(String searchTerms, String searchFields) {
        List<AuditSearchCriteriaDTO> criteria = new ArrayList<>();

        if (searchTerms == null || searchTerms.trim().isEmpty() ||
                searchFields == null || searchFields.trim().isEmpty()) {
            return criteria; // Return empty list if no search criteria provided
        }

        String[] terms = searchTerms.split(",");
        String[] fields = searchFields.split(",");

        if (terms.length != fields.length) {
            throw new IllegalArgumentException(
                    "Number of search terms (" + terms.length +
                            ") must match number of search fields (" + fields.length + ")");
        }

        for (int i = 0; i < terms.length; i++) {
            String term = terms[i].trim();
            String fieldName = fields[i].trim();

            if (!term.isEmpty() && !fieldName.isEmpty()) {
                try {
                    AuditFieldEnum field = AuditFieldEnum.valueOf(fieldName.toUpperCase());
                    if (!field.isSearchable()) {
                        throw new IllegalArgumentException("Field " + field + " is not searchable");
                    }
                    criteria.add(new AuditSearchCriteriaDTO(term, field));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid search field: " + fieldName, e);
                }
            }
        }

        return criteria;
    }

    // Helpers

    /**
     * Adds search constraints to the audit query based on the search criteria.
     *
     * @param query      The audit query to modify
     * @param searchTerm The search term to look for
     * @param searchType The field type to search in
     * @throws IllegalArgumentException if the search term is invalid for the given search type
     */
    private void addSearchConstraint(AuditQuery query, String searchTerm, AuditFieldEnum searchType) {
        if (!searchType.isSearchable()) {
            throw new IllegalArgumentException("Field " + searchType + " is not searchable");
        }

        switch (searchType) {
            case EXTERNAL_PATIENT_ID:
                // Search by external patient ID
                query.add(AuditEntity.property("externalPatientId").eq(searchTerm));
                break;

            case KEYCLOAK_ID:
                // Search by keycloak user ID in the revision info
                query.add(AuditEntity.revisionProperty("keycloakId").eq(searchTerm));
                break;

            case CONNECTOR_ID:
                // Search by connector ID in the revision info
                try {
                    Long connectorId = Long.parseLong(searchTerm);
                    query.add(AuditEntity.revisionProperty("connectorId").eq(connectorId));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid connector ID format: " + searchTerm, e);
                }
                break;

            case RUN_ID:
                // Search by run ID in the revision info
                try {
                    Long runId = Long.parseLong(searchTerm);
                    query.add(AuditEntity.revisionProperty("runId").eq(runId));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid run ID format: " + searchTerm, e);
                }
                break;

            case REVISION_TYPE:
                RevisionType revisionType = convertStringToRevisionType(searchTerm);
                query.add(AuditEntity.revisionType().eq(revisionType));
                break;

            case COHORT_ID:
                // Search by cohort ID in the patient entity
                try {
                    Long cohortId = Long.parseLong(searchTerm);
                    query.add(AuditEntity.property("cohort_id").eq(cohortId));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid cohort ID format: " + searchTerm, e);
                }
                break;

            default:
                throw new IllegalArgumentException("Unsupported search type: " + searchType);
        }
    }

    /**
     * Adds sorting to the audit query.
     * If no sort criteria is provided, defaults to revision timestamp descending.
     *
     * @param query        The audit query to modify
     * @param sortCriteria The sorting criteria, can be null for default sorting
     */
    private void addSorting(AuditQuery query, AuditSortCriteriaDTO sortCriteria) {
        if (sortCriteria == null) {
            // Default sorting: revision timestamp descending (most recent first)
            query.addOrder(AuditEntity.revisionProperty("timestamp").desc());
            return;
        }

        AuditFieldEnum sortField = sortCriteria.getSortField();
        SortDirectionEnum direction = sortCriteria.getSortDirection();

        if (!sortField.isSortable()) {
            throw new IllegalArgumentException("Field " + sortField + " is not sortable");
        }

        switch (sortField) {
            case REVISION_NUMBER:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.revisionNumber().asc());
                } else {
                    query.addOrder(AuditEntity.revisionNumber().desc());
                }
                break;

            case REVISION_TIMESTAMP:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.revisionProperty("timestamp").asc());
                } else {
                    query.addOrder(AuditEntity.revisionProperty("timestamp").desc());
                }
                break;

            case KEYCLOAK_ID:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.revisionProperty("keycloakId").asc());
                } else {
                    query.addOrder(AuditEntity.revisionProperty("keycloakId").desc());
                }
                break;

            case CONNECTOR_ID:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.revisionProperty("connectorId").asc());
                } else {
                    query.addOrder(AuditEntity.revisionProperty("connectorId").desc());
                }
                break;

            case RUN_ID:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.revisionProperty("runId").asc());
                } else {
                    query.addOrder(AuditEntity.revisionProperty("runId").desc());
                }
                break;

            case EXTERNAL_PATIENT_ID:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.property("externalPatientId").asc());
                } else {
                    query.addOrder(AuditEntity.property("externalPatientId").desc());
                }
                break;

            case REVISION_TYPE:
                if (direction == SortDirectionEnum.ASC) {
                    query.addOrder(AuditEntity.revisionType().asc());
                } else {
                    query.addOrder(AuditEntity.revisionType().desc());
                }
                break;

            default:
                throw new IllegalArgumentException("Unsupported sort field: " + sortField);
        }
    }

    /**
     * Executes the audit query and returns the result list.
     * Must be used with the query created with selectEntitiesOnly = false,
     *
     * @param query The audit query to execute
     * @return List of Object arrays from the audit query
     */
    private List<Object[]> executeAuditQuery(AuditQuery query) {
        // While this looks fishy af, the query was created with selectEntitiesOnly = false,
        // so this method does actually return for each result an Object array
        // 1. PatientMetaEntity
        // 2. CustomRevisionEntity (the revision info)
        // 3. RevisionType (ADD, MOD, DEL)
        // https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#revisions-of-entity
        // Ctrl +f for selectEntitiesOnly
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results;
    }

    /**
     * Converts the raw audit query results to TracabilityLogDTO objects.
     * <p>
     * Note: The audit query results are Object arrays because the query was created
     * with selectEntitiesOnly = false. Each result array contains:
     * [0] - The entity (PatientMetaEntity)
     * [1] - The revision entity (CustomRevisionEntity)
     * [2] - The revision type (RevisionType)
     *
     * @param results List of Object arrays from the audit query
     * @return List of TracabilityLogDTO objects
     */
    private List<TracabilityLog> convertToTracabilityLogDTOs(List<Object[]> results) {
        List<TracabilityLog> dtos = new ArrayList<>();

        for (Object[] result : results) {
            PatientEntity entity = (PatientEntity) result[0];
            CustomRevisionEntity revisionEntity = (CustomRevisionEntity) result[1];
            RevisionType revisionType = (RevisionType) result[2];

            TracabilityLog dto = new TracabilityLog();

            // Convert timestamp to Instant
            dto.setChangeDate(Instant.ofEpochMilli(revisionEntity.getTimestamp()));

            // Set entity information
            dto.setCohortId(Optional.ofNullable(entity.getCohort()).map(BaseEntity::getId).orElse(null));
            dto.setExternalPatientId(entity.getExternalPatientId());
            dto.setInternalPatientId(entity.getId());
            dto.setDataEntriesVersion(entity.getDataEntriesVersion());

            // Set revision type directly
            dto.setChangeType(revisionType);
            dto.setRevisionId(revisionEntity.getId());

            // Set revision information
            dto.setKeycloakUserId(revisionEntity.getKeycloakId());
            dto.setConnectorId(revisionEntity.getConnectorId());
            dto.setRunId(revisionEntity.getRunId());

            dtos.add(dto);
        }

        return dtos;
    }

    private List<TracabilityDataChangeLog> convertToTracabilityDataChangeLogs(List<Object[]> results) {
        List<TracabilityDataChangeLog> dtos = new ArrayList<>();

        for (Object[] result : results) {
            PatientDataEntryEntity entity = (PatientDataEntryEntity) result[0];
            CustomRevisionEntity revisionEntity = (CustomRevisionEntity) result[1];
            RevisionType revisionType = (RevisionType) result[2];

            TracabilityDataChangeLog dto = new TracabilityDataChangeLog();

            dto.setDataEntry(entity);

            // Set revision type directly
            dto.setChangeType(revisionType);
            dto.setRevisionId(revisionEntity.getId());

            dtos.add(dto);
        }

        return dtos;
    }

    /**
     * Converts string representation to Hibernate Envers RevisionType.
     * Accepts the direct Hibernate Envers string values: ADD, MOD, DEL.
     *
     * @param revisionTypeString The revision type as string (ADD, MOD, DEL)
     * @return The corresponding Hibernate Envers RevisionType
     * @throws IllegalArgumentException if the revision type string is not valid
     */
    private RevisionType convertStringToRevisionType(String revisionTypeString) {
        if (revisionTypeString == null || revisionTypeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Revision type cannot be null or empty");
        }

        String upperCaseType = revisionTypeString.trim().toUpperCase();
        // Internally the revision types are byte 0, 1 and 2, but it's nicer for the frontend
        // to use string values
        return switch (upperCaseType) {
            case "ADD" -> RevisionType.ADD;
            case "MOD" -> RevisionType.MOD;
            case "DEL" -> RevisionType.DEL;
            default -> throw new IllegalArgumentException("Invalid revision type: " + revisionTypeString +
                    ". Valid values are: ADD, MOD, DEL");
        };
    }
}
