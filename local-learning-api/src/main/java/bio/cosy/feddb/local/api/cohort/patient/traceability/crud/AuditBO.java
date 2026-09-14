package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryHelper;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AuditBO {

    @Inject
    AuditAO auditAO;

    /**
     * Main business method to query audit logs with full parameter validation and processing.
     * This method handles all the business logic for parameter extraction, validation, and coordination.
     *
     * @param cohortId      The cohort ID to filter by
     * @param page          Page number (0-based)
     * @param size          Page size
     * @param sortField     The field to sort by
     * @param sortDirection The sort direction
     * @param searchTerms   Comma-separated search terms
     * @param searchFields  Comma-separated search field names
     * @return PagedResponse containing the audit logs
     * @throws IllegalArgumentException if parameters are invalid
     */
    @Transactional
    public PagedResponse<PatientDataTraceabilityLogDto> queryAuditLogs(
            Long cohortId,
            int page,
            int size,
            AuditFieldEnum sortField,
            SortDirectionEnum sortDirection,
            List<String> searchTerms,
            List<AuditFieldEnum> searchFields) {

        // Validate input parameters
        validateParameters(cohortId, page, size, sortField, sortDirection);

        // Parse and validate search criteria
        List<AuditSearchCriteriaDTO> searchCriteria = parseAndValidateSearchCriteria(searchTerms, searchFields);

        // Always add cohort ID as a search criteria to ensure proper filtering
        //TODO AFTER USER<-> COHORT SYSTEM IS AVAILABLE addCohortSearchCriteria(searchCriteria, cohortId);

        // Build sort criteria
        AuditSortCriteriaDTO sortCriteria = buildSortCriteria(sortField, sortDirection);

        // Calculate pagination parameters
        int firstResult = page * size;

        // Query the data
        List<PatientDataTraceabilityLogDto> results = auditAO.queryAuditChanges(
                searchCriteria,
                firstResult,
                size,
                sortCriteria
        ).stream().map(PatientDataTraceabilityLogDto::new).toList();

        long totalCount = auditAO.countAuditChanges(searchCriteria);

        return new PagedResponse<>(results, page, size, totalCount);
    }

    public PatientDataTraceabilityDetailLogDto getPatientChangesDetail(Long patientId, Integer revId) {
        TracabilityLog overAllChange = auditAO.getPatientChangesForRev(patientId, revId);
        List<TracabilityDataChangeLog> changes = auditAO.getPatientDataChangesForRev(patientId, revId);
        List<TracabilityDataChangeLog> prevChanges = new ArrayList<>();
        Long version = overAllChange.getDataEntriesVersion();
        if (version > 0) {
            //find revision for the previous data entries version
            CustomRevisionEntity rev = auditAO.getRevForDataEntriesVersion(patientId, version - 1);
            if (rev != null) {
                prevChanges = auditAO.getPatientDataChangesForRev(patientId, rev.getId());
            }
        }
        return new PatientDataTraceabilityDetailLogDto(overAllChange, mergeChanges(changes, prevChanges));
    }

    private List<PatientDataChangeLogDto> mergeChanges(List<TracabilityDataChangeLog> changes, List<TracabilityDataChangeLog> prevChanges) {
        List<PatientDataChangeLogDto> mergedChanges = new ArrayList<>();
        for (TracabilityDataChangeLog change : changes) {
            PatientDataChangeLogDto dto = new PatientDataChangeLogDto();
            dto.setChangeType(change.getChangeType());
            dto.setRevId(change.getRevisionId());

            SchemaNodeEntity schemaNode = change.getDataEntry().getSchemaNode();
            dto.setSchemaNodeId(schemaNode.getId());
            dto.setPropertyName(schemaNode.getName());
            DataTypes type = schemaNode.getDataType().getType();
            dto.setCurrentData(PatientDataEntryHelper.getValue(change.getDataEntry(), type));
            // Find previous value for the same schema node
            for (TracabilityDataChangeLog prevChange : prevChanges) {
                if (prevChange.getDataEntry().getSchemaNode().getId().equals(schemaNode.getId())) {
                    dto.setPreviousData(PatientDataEntryHelper.getValue(prevChange.getDataEntry(), type));
                    break;
                }
            }
            mergedChanges.add(dto);
        }
        return mergedChanges;
    }

    /**
     * Validates the basic query parameters.
     *
     * @param cohortId      The cohort ID
     * @param page          The page number
     * @param size          The page size
     * @param sortField     The sort field
     * @param sortDirection The sort direction
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateParameters(Long cohortId, int page, int size,
                                    AuditFieldEnum sortField, SortDirectionEnum sortDirection) {
        if (cohortId == null || cohortId <= 0) {
            //throw new IllegalArgumentException("Cohort ID must be a positive number, got: " + cohortId);
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page number must be non-negative, got: " + page);
        }

        if (size <= 0 || size > 1000) {
            throw new IllegalArgumentException("Page size must be between 1 and 1000, got: " + size);
        }

        if (sortField != null && !sortField.isSortable()) {
            throw new IllegalArgumentException("Field '" + sortField + "' is not sortable.");
        }

        Log.debug("Parameters validated successfully for cohort " + cohortId + " with page " + page + ", size " + size);
    }

    /**
     * Validates and builds search criteria from list parameters.
     *
     * @param searchTerms  List of search terms
     * @param searchFields List of search field enums
     * @return List of validated SearchCriteria
     * @throws IllegalArgumentException if search criteria are invalid
     */
    private List<AuditSearchCriteriaDTO> parseAndValidateSearchCriteria(List<String> searchTerms, List<AuditFieldEnum> searchFields) {
        try {
            // Validate that both lists have the same size if both are provided
            if (searchTerms != null && searchFields != null && searchTerms.size() != searchFields.size()) {
                throw new IllegalArgumentException("Number of search terms (" + searchTerms.size() +
                        ") must match number of search fields (" + searchFields.size() + ")");
            }

            // If either list is null or empty, return empty criteria
            if (searchTerms == null || searchFields == null || searchTerms.isEmpty() || searchFields.isEmpty()) {
                Log.debug("No search criteria provided");
                return new java.util.ArrayList<>();
            }

            // Validate that all fields are searchable
            for (AuditFieldEnum field : searchFields) {
                if (!field.isSearchable()) {
                    throw new IllegalArgumentException("Field '" + field + "' is not searchable. Searchable fields are: " + getSearchableFields());
                }
            }

            // Build search criteria list
            List<AuditSearchCriteriaDTO> criteria = new java.util.ArrayList<>();
            for (int i = 0; i < searchTerms.size(); i++) {
                String term = searchTerms.get(i);
                AuditFieldEnum field = searchFields.get(i);

                // Validate term is not null or empty
                if (term == null || term.trim().isEmpty()) {
                    throw new IllegalArgumentException("Search term at position " + i + " cannot be null or empty");
                }

                criteria.add(new AuditSearchCriteriaDTO(term.trim(), field));
            }

            Log.debug("Built " + criteria.size() + " search criteria");
            return criteria;
        } catch (IllegalArgumentException e) {
            Log.warn("Invalid search criteria - terms: " + searchTerms + ", fields: " + searchFields + ", error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.warn("Unexpected error building search criteria - terms: " + searchTerms + ", fields: " + searchFields + ", error: " + e.getMessage());
            throw new IllegalArgumentException("Failed to build search criteria: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a list of all searchable field names for error messages.
     */
    private String getSearchableFields() {
        return java.util.Arrays.stream(AuditFieldEnum.values())
                .filter(AuditFieldEnum::isSearchable)
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Adds the cohort ID as a mandatory search criteria to ensure proper filtering.
     * This method ensures that all audit queries are automatically filtered by the given cohort.
     *
     * @param searchCriteria The existing list of search criteria
     * @param cohortId       The cohort ID to add as a filter
     */
    private void addCohortSearchCriteria(List<AuditSearchCriteriaDTO> searchCriteria, Long cohortId) {
        // Check if cohort criteria already exists to avoid duplicates
        boolean cohortCriteriaExists = searchCriteria.stream()
                .anyMatch(criteria -> AuditFieldEnum.COHORT_ID.equals(criteria.getSearchType()));

        if (!cohortCriteriaExists) {
            AuditSearchCriteriaDTO cohortCriteria = new AuditSearchCriteriaDTO(cohortId.toString(), AuditFieldEnum.COHORT_ID);
            searchCriteria.add(cohortCriteria);
            Log.debug("Added cohort ID search criteria: " + cohortId);
        } else {
            Log.debug("Cohort ID search criteria already exists, skipping addition");
        }
    }

    /**
     * Builds sort criteria from the provided parameters.
     *
     * @param sortField     The field to sort by
     * @param sortDirection The sort direction
     * @return SortCriteria object, or null for default sorting
     */
    private AuditSortCriteriaDTO buildSortCriteria(AuditFieldEnum sortField, SortDirectionEnum sortDirection) {
        if (sortField == null) {
            Log.debug("No sort field specified, using default sorting");
            return null; // Will use default sorting in AO
        }

        SortDirectionEnum direction = sortDirection != null ? sortDirection : SortDirectionEnum.DESC;
        Log.debug("Built sort criteria: " + sortField + " " + direction);
        return new AuditSortCriteriaDTO(sortField, direction);
    }
}
