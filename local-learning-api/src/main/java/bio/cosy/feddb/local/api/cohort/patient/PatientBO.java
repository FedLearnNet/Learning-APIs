package bio.cosy.feddb.local.api.cohort.patient;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryAO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryBO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;


@ApplicationScoped
public class PatientBO {
    @Inject
    PatientAO patientMetaAO;
    @Inject
    PatientMapper patientDataMapper;
    @Inject
    CohortAO cohortAO;
    @Inject
    PatientDataEntryBO patientDataEntryBO;
    @Inject
    PatientDataEntryAO patientDataEntryAO;
    @Inject
    TimeSeriesReducer timeSeriesReducer;
    @Inject
    CohortBO cohortBO;

    public PatientDTO getPatientDataById(Long cohortId, Long internalPatientId) {
        Optional<PatientEntity> patientMeta = patientMetaAO.findByInternalId(cohortId, internalPatientId);
        return patientMeta.map(patientEntity -> patientDataMapper.entityToDto(patientEntity)).orElse(null);
    }

    public PatientDTO getPatientDataByExternalId(Long cohortId, String externalPatientId) {
        Optional<PatientEntity> patientMeta = patientMetaAO.findByExternalPatientId(cohortId, externalPatientId);
        return patientMeta.map(patientEntity -> patientDataMapper.entityToDto(patientEntity)).orElse(null);
    }


    public List<PatientDTO> listPatientData(Long cohortId) {
        List<PatientEntity> patientMetaEntities = patientMetaAO.findAllByCohortId(cohortId);
        return patientMetaEntities.stream()
                .map(patientDataMapper::entityToDto)
                .toList();
    }

    @Transactional
    public List<PatientDTO> listPatientDataTransactional(Long cohortId) {
        return listPatientData(cohortId);
    }


    public List<PatientReferenceDTO> listPatientReferences(Long cohortId, int limit) {
        return patientMetaAO.findReferencesByCohortId(cohortId, limit);
    }

    public List<SearchResultDTO<PatientDTO>> search(String query, String keycloakId) {
        return cohortBO.getAll(keycloakId).stream()
                .flatMap(cohort -> listPatientData(cohort.getId()).stream())
                .map(patient -> {
                    String title = patient.getExternalPatientId();
                    int score = SearchScoreUtil.score(query, title, String.valueOf(patient.getId()),
                            String.valueOf(patient.getCohortId()));
                    return SearchScoreUtil.toResult(SearchResultType.PATIENT, patient, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    // For the HTTP Endpoints
    public PatientDTO createPatientData(Long cohortId, PatientDTO createPatientDataDTO) {
        // Implementation to create a new patient data record
        validateCohortPathVsDTO(cohortId, createPatientDataDTO);
        if (patientMetaAO.findByExternalPatientId(cohortId, createPatientDataDTO.getExternalPatientId()).isPresent()) {
            throw new IllegalStateException("Patient with the same external ID already exists in the cohort");
        }
        CohortEntity cohort = getValidCohort(cohortId);
        PatientEntity patientMetaEntity = patientDataMapper.dtoToEntity(createPatientDataDTO);
        patientMetaEntity.setCohort(cohort);
        patientMetaEntity.setDataEntriesVersion(0L);
        patientDataEntryBO.setImportSchemaGroupId(patientMetaEntity.getDataEntries());
        patientMetaAO.persist(patientMetaEntity);
        return patientDataMapper.entityToDto(patientMetaEntity);
    }

    public void deletePatientData(Long cohortId, Long internalPatientId) {
        // Implementation to delete patient data by internal ID
        // Careful here, we delete the patient data but NOT tracability data!
        // So we keep the PatientMetaEntity but delete all related PatientDataEntity records
        Optional<PatientEntity> optionalPatientMeta = patientMetaAO.findByInternalId(cohortId, internalPatientId);
        if (optionalPatientMeta.isEmpty()) {
            return; // No patient found, nothing to delete
        } else {
            deletePatientData(optionalPatientMeta.get());
        }
    }

    @Transactional
    public long deleteAllPatients(Long cohortId) {
        // Implementation to delete all patients of a cohort
        // Careful here, we delete the patient data but NOT tracability data!
        // So we keep the PatientMetaEntity but delete all related PatientDataEntity records
        return patientMetaAO.deleteAllPatients(cohortId);
    }

    public void deletePatientData(PatientEntity patient) {
        // Deletes the patient data but NOT tracability data!
        // So we keep the PatientMetaEntity but delete all related PatientDataEntryEntity records
        if (patient == null) {
            return; // Nothing to delete
        }
        patient.getDataEntries().clear();
        patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
        patientMetaAO.persist(patient);
    }

    public void hardDeleteInternalId(Long cohortId, Long internalPatientId) {
        Optional<PatientEntity> optionalPatientMeta = patientMetaAO.findByInternalId(cohortId, internalPatientId);
        if (optionalPatientMeta.isEmpty()) {
            return; // No patient found, nothing to delete
        } else {
            PatientEntity patientMetaEntity = optionalPatientMeta.get();
            // Delete the whole record, including all related data
            // cascade will ensure that all related data is deleted
            patientMetaAO.delete(patientMetaEntity);
        }
    }

    public PagedResponse<PatientDTO> getReducedPatientData(Long cohortId, String numericReductionMethod, String nonNumericReductionMethod, Integer page, Integer pageSize) {
        // Parse reduction methods with validation first
        TimeSeriesReductionMethod numericMethod;
        TimeSeriesReductionMethod nonNumericMethod;

        try {
            numericMethod = TimeSeriesReductionMethod.fromValueForNumeric(numericReductionMethod);
            nonNumericMethod = TimeSeriesReductionMethod.fromValueForNonNumeric(nonNumericReductionMethod);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid reduction method: " + e.getMessage(), e);
        }

        // Get the total count for pagination metadata
        long totalCount = patientMetaAO.countByCohortId(cohortId);

        // Get only the patients for the requested page (database-level pagination)
        // Convert from 1-based page (API) to 0-based page (Panache)
        int panachePage = page - 1;
        List<PatientEntity> patientMetaEntities = patientMetaAO.findAllByCohortId(cohortId, panachePage, pageSize);

        List<Long> patientIds = patientMetaEntities.stream()
                .map(PatientEntity::getId)
                .toList();
        Map<Long, List<PatientDataEntryEntity>> entriesByPatientId = patientDataEntryAO.findReducedPageEntries(cohortId, patientIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getPatient().getId()));

        // Apply reduction only to the fetched patients
        List<PatientDTO> reducedData = patientMetaEntities.stream()
                .map(patient -> getReducedPatientDataForSinglePatient(
                        patient,
                        entriesByPatientId.getOrDefault(patient.getId(), List.of()),
                        numericMethod,
                        nonNumericMethod))
                .toList();

        return new PagedResponse<>(reducedData, page, pageSize, totalCount);
    }

    public Map<Long, List<Long>> groupPatientIdsByCohortId(List<Long> patientIds) {
        return patientMetaAO.groupIdsByCohortId(patientIds);
    }

    private PatientDTO getReducedPatientDataForSinglePatient(PatientEntity patient,
                                                             List<PatientDataEntryEntity> dataEntries,
                                                             TimeSeriesReductionMethod numericMethod,
                                                             TimeSeriesReductionMethod nonNumericMethod) {
        // Group data entries by schema node
        Map<Long, List<PatientDataEntryEntity>> entriesBySchemaNode = dataEntries.stream()
                .collect(java.util.stream.Collectors.groupingBy(entry -> entry.getSchemaNode().getId()));

        Set<PatientDataEntryDTO> reducedEntryDTOs = new HashSet<>();

        // Process each schema node group
        for (Map.Entry<Long, List<PatientDataEntryEntity>> schemaNodeGroup : entriesBySchemaNode.entrySet()) {
            List<PatientDataEntryEntity> entries = schemaNodeGroup.getValue();

            if (entries.isEmpty()) {
                continue;
            }

            try {
                // Determine if this schema node contains numeric data
                boolean isNumeric = TimeSeriesReducer.isAllNumericData(entries);

                // Apply the appropriate reduction method
                Optional<PatientDataEntryDTO> reducedEntry;
                try {
                    if (isNumeric) {
                        reducedEntry = timeSeriesReducer.reduceNumeric(entries, numericMethod);
                        // If reduction failed, fall back to latest for numeric data
                        if (reducedEntry.isEmpty()) {
                            reducedEntry = timeSeriesReducer.reduceNumeric(entries, TimeSeriesReductionMethod.LATEST);
                        }
                    } else {
                        reducedEntry = timeSeriesReducer.reduceNonNumeric(entries, nonNumericMethod);
                        // If reduction failed, fall back to latest for non-numeric data
                        if (reducedEntry.isEmpty()) {
                            reducedEntry = timeSeriesReducer.reduceNonNumeric(entries, TimeSeriesReductionMethod.LATEST);
                        }
                    }

                    reducedEntry.ifPresent(reducedEntryDTOs::add);
                } catch (IllegalStateException e) {
                    // Let IllegalStateException bubble up - indicates serious internal data state issues
                    throw e;
                } catch (Exception e) {
                    // Handle any unexpected exceptions during reduction
                    throw new IllegalArgumentException("Error reducing data for schema node " + schemaNodeGroup.getKey() +
                            ": " + e.getMessage(), e);
                }
            } catch (IllegalStateException e) {
                // Let IllegalStateException bubble up - indicates serious internal data state issues (e.g., from isAllNumericData)
                throw e;
            }
        }

        // Create a patient DTO with reduced data entries (no entity creation to avoid Hibernate issues)
        // We could use the mapper but that would unnecessarily also map the data entries
        // just to then replace them, this is why we do it this way manually
        PatientDTO reducedPatientDTO = new PatientDTO();
        reducedPatientDTO.setId(patient.getId());
        reducedPatientDTO.setVersion(patient.getVersion()); // Copy the version from the entity
        reducedPatientDTO.setExternalPatientId(patient.getExternalPatientId());
        reducedPatientDTO.setCohortId(patient.getCohort().getId());
        reducedPatientDTO.setDataEntriesVersion(patient.getDataEntriesVersion()); // Copy the data entries version
        reducedPatientDTO.setDataEntries(reducedEntryDTOs);

        return reducedPatientDTO;
    }

    // Helpers
    private void validateCohortPathVsDTO(Long cohortId, PatientDTO patientDataDTO) {
        Long cohortIdDTO = patientDataDTO.getCohortId();
        if (cohortId == null || !cohortId.equals(cohortIdDTO)) {
            throw new IllegalArgumentException("Cohort ID in DTO does not match the provided cohort ID");
        }
    }

    private CohortEntity getValidCohort(Long cohortId) {
        if (cohortId == null) {
            throw new IllegalArgumentException("Cohort with ID is null");
        }
        Optional<CohortEntity> cohortOpt = cohortAO.findByIdOptional(cohortId);
        if (cohortOpt.isEmpty()) {
            throw new IllegalArgumentException("Cohort with ID " + cohortId + " does not exist");
        }
        return cohortOpt.get();
    }
}
