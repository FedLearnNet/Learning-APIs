package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientMapper;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PatientDataEntryBO {

    @Inject
    PatientDataEntryAO patientDataEntryAO;

    @Inject
    PatientAO patientMetaAO;

    @Inject
    CohortAO cohortAO;

    @Inject
    PatientMapper patientDataMapper;

    @Inject
    PatientDataEntryExportMapper patientDataEntryExportMapper;

    // For HTTP Endpoints
    public PatientDataEntryDTO updateDataEntry(Long cohortId, Long internalPatientId, Long id, PatientDataEntryDTO dto) {
        // Validate cohort and patient existence, get the patient entity
        PatientEntity patient = validateCohortAndPatient(cohortId, internalPatientId);
        validateValueIsPresent(dto);
        PatientDataEntryEntity entity = patientDataMapper.dtoToEntity(dto);
        entity.setPatient(patient);
        entity.setCreatedAt(null);
        setImportSchemaGroupId(List.of(entity));
        entity = patientDataEntryAO.mergeDataEntries(entity);
        patientMetaAO.increaseDataEntriesVersion(internalPatientId);
        return patientDataMapper.entityToDto(entity);
    }

    public List<PatientDataEntryDTO> updateDataEntries(Long cohortId, Long internalPatientId, List<PatientDataEntryDTO> updateDTOs) {
        // Validate cohort and patient existence, get the patient entity
        PatientEntity patient = validateCohortAndPatient(cohortId, internalPatientId);
        validateNoDuplicateSchemaNodes(cohortId, internalPatientId, updateDTOs);
        updateDTOs.forEach(this::validateValueIsPresent);

        // Separate entries into updates (with id) and creates (without id)
        List<PatientDataEntryEntity> entriesToUpdate = new ArrayList<>();
        List<PatientDataEntryEntity> entriesToCreate = new ArrayList<>();

        for (PatientDataEntryDTO dto : updateDTOs) {
            PatientDataEntryEntity entity = patientDataMapper.dtoToEntity(dto);
            entity.setPatient(patient);

            if (dto.getId() != null) {
                // Entry has an ID, so it's an update
                entriesToUpdate.add(entity);
            } else {
                // Entry has no ID, so it's a create
                // Ensure version is set to 0 for new entries
                if (entity.getVersion() == null) {
                    entity.setVersion(0L);
                }
                entriesToCreate.add(entity);
            }
        }

        List<PatientDataEntryEntity> resultEntries = new ArrayList<>();

        if (!entriesToUpdate.isEmpty()) {
            setImportSchemaGroupId(entriesToUpdate);
            try {
                List<PatientDataEntryEntity> updated = entriesToUpdate
                        .stream()
                        .map(e -> patientDataEntryAO.mergeDataEntries(e))
                        .collect(Collectors.toList());
                resultEntries.addAll(updated);
            } catch (ClientErrorException e) {
                throw new IllegalArgumentException("Invalid data entry update: " + e.getMessage(), e);
            }
        }

        if (!entriesToCreate.isEmpty()) {
            setImportSchemaGroupId(entriesToCreate);
            patient.getDataEntries().addAll(entriesToCreate);
            entriesToCreate.forEach(entry -> patientDataEntryAO.persist(entry));
            resultEntries.addAll(entriesToCreate);
        }

        if (!resultEntries.isEmpty()) {
            patientMetaAO.increaseDataEntriesVersion(internalPatientId);
        }

        return resultEntries.stream()
                .map(patientDataMapper::entityToDto)
                .toList();
    }

    public List<PatientDataEntryDTO> createMultipleDataEntries(Long cohortId, Long internalPatientId, List<PatientDataEntryDTO> createDTOs) {
        // Validate cohort and patient existence, get the patient entity
        PatientEntity patient = validateCohortAndPatient(cohortId, internalPatientId);
        validateNoDuplicateSchemaNodes(cohortId, internalPatientId, createDTOs);
        List<PatientDataEntryEntity> entries = createDTOs.stream()
                .map(dto -> patientDataMapper.dtoToEntity(dto)) // create a new entity for each DTO
                .map(entity -> { // ensure they are linked to the patient
                    entity.setPatient(patient);
                    return entity;
                })
                .toList();
        setImportSchemaGroupId(entries);
        patient.getDataEntries().addAll(entries);
        entries.stream().forEach(entry -> patientDataEntryAO.persist(entry));
        patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
        patientMetaAO.persist(patient);
        return entries.stream()
                .map(patientDataMapper::entityToDto)
                .toList();
    }

    public PatientDataEntryDTO createSingleDataEntries(Long cohortId, Long internalPatientId, PatientDataEntryDTO createDTO) {
        // Validate cohort and patient existence, get the patient entity
        PatientEntity patient = validateCohortAndPatient(cohortId, internalPatientId);
        PatientDataEntryEntity entry = patientDataMapper.dtoToEntity(createDTO);
        entry.setPatient(patient);
        setImportSchemaGroupId(List.of(entry));
        patient.getDataEntries().add(entry);
        patientDataEntryAO.persist(entry);
        patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
        patientMetaAO.persist(patient);
        return patientDataMapper.entityToDto(entry);
    }


    public void deleteMultipleDataEntries(Long cohortId, Long internalPatientId, Set<Long> deleteIds) {
        // Validate cohort and patient existence, get the patient entity
        PatientEntity patient = validateCohortAndPatient(cohortId, internalPatientId);
        if (deleteIds == null || deleteIds.isEmpty()) {
            throw new IllegalArgumentException("Delete IDs list cannot be null or empty");
        }

        // Find and remove the data entries
        Set<PatientDataEntryEntity> entriesToDelete = patient.getDataEntries().stream()
                .filter(entry -> deleteIds.contains(entry.getId()))
                .collect(Collectors.toSet());
        patient.getDataEntries().removeAll(entriesToDelete);
        patient.setDataEntriesVersion(patient.getDataEntriesVersion() + 1);
        patientMetaAO.persist(patient);
    }

    // Helpers
    public void setImportSchemaGroupId(Iterable<PatientDataEntryEntity> entries) {
        // For each schemaGroup of the entries creates and sets a new import schema group ID
        // Assumes a flattened list, so dataentries have different groups
        HashMap<Long, String> groupNodeID2UUID = new HashMap<>();
        HashSet<Long> schemaNodeIdsUsed = new HashSet<>();
        for (PatientDataEntryEntity entry : entries) {
            SchemaNodeEntity schemaNode = entry.getSchemaNode();
            if (schemaNode == null || schemaNode.getId() == null) {
                throw new IllegalArgumentException("Schema node ID must be provided for data entries");
            }
            if (!schemaNodeIdsUsed.add(schemaNode.getId())) {
                throw new IllegalArgumentException("Duplicate schema node ID " + schemaNode.getId() +
                        " found in batch. Only one data entry per schema node is allowed per batch.");
            }
            SchemaNodeEntity parentNode = schemaNode.getParent();
            if (parentNode == null || parentNode.getId() == null || parentNode.getType() == null ||
                    !(parentNode.getType() == SchemaNodeType.GROUP || parentNode.getType() == SchemaNodeType.ROOT)) {
                throw new IllegalArgumentException("Data entry must be part of a group");
            }
            String groupId = Long.toString(parentNode.getId());
            String groupUUID = groupNodeID2UUID.computeIfAbsent(parentNode.getId(), id -> UUID.randomUUID().toString());
            String importSchemaGroupId = "FRONTEND_" + groupId + "_" + groupUUID;
            entry.setImportSchemaGroupId(importSchemaGroupId);
        }
    }

    public List<PatientDataEntryExportDTO> findByIdsAndDataIds(List<Long> patientIds, List<SelectedDataIdsDTO> selectedDataIds) {
        return patientDataEntryAO.findByIdsAndDataIds(patientIds, selectedDataIds)
                .stream()
                .map(patientDataEntryExportMapper::toPatientDataEntryExportDTO)
                .toList();
    }

    public List<PatientDataEntryExportDTO> findByIds(List<Long> patientIds) {
        return patientDataEntryAO.findByPatientIds(patientIds)
                .stream()
                .map(patientDataEntryExportMapper::toPatientDataEntryExportDTO)
                .toList();
    }

    public List<PatientDataEntryExportDTO> findByCohortAndPatient(Long cohortId, Long internalPatientId) {
        return patientDataEntryAO.findByCohortAndPatient(cohortId, internalPatientId)
                .stream()
                .map(patientDataEntryExportMapper::toPatientDataEntryExportDTO)
                .toList();
    }

    public List<PatientDataEntryExportDTO> findByCohortAndPatientIds(Long cohortId, List<Long> patientIds) {
        return patientDataEntryAO.findByCohortAndPatientIds(cohortId, patientIds)
                .stream()
                .map(patientDataEntryExportMapper::toPatientDataEntryExportDTO)
                .toList();
    }

    public List<PatientDataEntryExportDTO> findByPatientIds(List<Long> patientIds) {
        return patientDataEntryAO.findByPatientIds(patientIds)
                .stream()
                .map(patientDataEntryExportMapper::toPatientDataEntryExportDTO)
                .toList();
    }

    private void validateNoDuplicateSchemaNodes(Long cohortId, Long internalPatientId, List<PatientDataEntryDTO> createDTOs) {
        // Validate that there's only one data entry per schema node ID
        Set<Long> schemaNodeIds = new HashSet<>();
        for (PatientDataEntryDTO createDTO : createDTOs) {
            // Check for duplicate schema node IDs in the batch
            if (createDTO.getSchemaNodeId() == null) {
                throw new IllegalArgumentException("Schema node ID must be provided for data entries");
            }

            if (!schemaNodeIds.add(createDTO.getSchemaNodeId())) {
                throw new IllegalArgumentException("Duplicate schema node ID " + createDTO.getSchemaNodeId() +
                        " found in batch. Only one data entry per schema node is allowed per batch.");
            }
        }
    }

    private void validateValueIsPresent(PatientDataEntryDTO dto) {
        if (dto.getValue() == null && !Boolean.TRUE.equals(dto.getIsNullValue())) {
            throw new IllegalArgumentException("Value must be provided for data entries");
        }
    }

    private PatientEntity validateCohortAndPatient(Long cohortId, Long internalPatientId) {
        // Validate cohort exists
        CohortEntity cohort = cohortAO.findById(cohortId);
        if (cohort == null) {
            throw new NotFoundException("Cohort with ID " + cohortId + " does not exist");
        }

        // Validate patient exists
        Optional<PatientEntity> patientOpt = patientMetaAO.findByInternalId(cohortId, internalPatientId);
        if (patientOpt.isEmpty()) {
            throw new NotFoundException("Patient with internal ID " + internalPatientId + " does not exist in cohort " + cohortId);
        }

        return patientOpt.get();
    }
}
