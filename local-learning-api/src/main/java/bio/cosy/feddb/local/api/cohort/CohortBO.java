package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunBO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionBO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.crud.PatientDataAuditAO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.queryability.CohortQueryAbilityAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunAO;
import bio.cosy.feddb.local.api.schema.SchemaBO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CohortBO extends BaseBo<CohortDTO, CohortEntity, CohortAO, CohortMapper> {

    private static final Set<Long> DELETIONS_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    @Inject
    Instance<CohortBO> self;

    @Inject
    PatientToolRunBO patientToolRunBO;

    @Inject
    SchemaBO schemaBO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    CohortCriterionBO cohortCriterionBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunAO connectorRunAO;

    @Inject
    PatientAO patientAO;

    @Inject
    PatientDataAuditAO patientDataAuditAO;

    @Inject
    SchemaNodeAO schemaNodeAO;

    @Inject
    FLNetClientConfig config;

    @Inject
    CohortMemberAuthBO cohortAuthBO;

    @Inject
    CohortQueryAbilityAO cohortQueryAbilityAO;

    public boolean isDeletionInProgress(Long cohortId) {
        return DELETIONS_IN_PROGRESS.contains(cohortId);
    }

    public CohortDetailDTO findById(Long id, String keycloakId) {
        Optional<CohortEntity> entityOptional = ao.findByIdOptional(id);
        CohortDetailDTO dto = entityOptional
                .filter(entity -> cohortAuthBO.isMember(entity, keycloakId))
                .map(mapper::entityToDetailDto)
                .orElseThrow(() -> new NotFoundException("CohortEntity not found"));
        dto.setDeletionInProgress(isDeletionInProgress(id));
        return dto;
    }

    public CohortDetailDTO findById(Long id) {
        Optional<CohortEntity> entityOptional = ao.findByIdOptional(id);
        return entityOptional
                .map(mapper::entityToDetailDto)
                .orElseThrow(() -> new NotFoundException("CohortEntity not found"));
    }

    public List<CohortDTO> getAll(String keycloakId) {
        List<CohortEntity> cohorts = ao.listAll();
        return cohorts.stream()
                .filter(entity -> cohortAuthBO.isMember(entity, keycloakId))
                .map(entity -> {
                    CohortDTO dto = mapper.entityToDto(entity);
                    dto.setDeletionInProgress(isDeletionInProgress(entity.getId()));
                    return dto;
                })
                .toList();
    }

    public List<SearchResultDTO<CohortDTO>> search(String query, String keycloakId) {
        return getAll(keycloakId).stream()
                .map(cohort -> {
                    String title = cohort.getName();
                    int score = SearchScoreUtil.score(query, title, cohort.getDescription(), cohort.getGlobalSchemaId());
                    return SearchScoreUtil.toResult(SearchResultType.COHORT, cohort, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    public CohortNameHealthDTO checkNameHealth(String name, Long excludeCohortId) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Cohort name is required");
        }
        String trimmed = name.trim();
        if (excludeCohortId != null && ao.findByIdOptional(excludeCohortId).isEmpty()) {
            throw new NotFoundException("CohortEntity not found");
        }

        CohortNameHealthDTO health = new CohortNameHealthDTO();
        health.setName(trimmed);
        health.setNameExists(ao.existsByNameExcludingId(trimmed, excludeCohortId));
        return health;
    }

    private void ensureNameAvailable(String name, Long excludeCohortId) {
        if (ao.existsByNameExcludingId(name.trim(), excludeCohortId)) {
            throw new WebApplicationException(
                    "Cohort with name '" + name.trim() + "' already exists",
                    Response.Status.CONFLICT);
        }
    }

    public CohortDTO create(CreateCohortDTO createDTO, String keycloakId) {
        boolean addDefaultPermission = config.cohort().defaultCohortPermission().enabled();
        return create(createDTO, keycloakId, addDefaultPermission);
    }

    @Transactional
    public CohortDTO create(CreateCohortDTO createDTO, String keycloakId, boolean addDefaultPermission) {
        ensureNameAvailable(createDTO.getName(), null);
        CohortEntity entity = mapper.createDtoToEntity(createDTO, keycloakId);
        updatePublicationStatus(entity, createDTO.getStatus());
        // Persist cohort first so it's managed before schema nodes reference it
        ao.persist(entity);
        Log.debugf("Created cohort %s", entity);
        // Subscribe the relevant schema
        Set<SchemaNodeEntity> schemaNodes = schemaBO.subscribe(UUID.fromString(createDTO.getGlobalSchemaID()));
        // Now we can create the cohort and connect the schema nodes
        // Set the owning side for each schema node
        for (SchemaNodeEntity node : schemaNodes) {
            node.setCohort(entity);
        }
        // Connect schema nodes to the cohort (inverse side)
        entity.setSchemaNodes(schemaNodes);
        if (addDefaultPermission) {
            permissionBO.addDefault(entity);
        }
        cohortCriterionBO.replaceCriteria(entity, createDTO.getCriteria());
        return mapper.entityToDto(entity);
    }

    public CohortDetailDTO update(Long id, UpdateCohortDTO updateDTO, String keycloakId) {
        assertNotDeleting(id);
        CohortEntity entity = ao.findById(id);
        if (entity == null) {
            throw new NotFoundException("CohortEntity not found");
        }
        cohortAuthBO.checkForEdit(entity, keycloakId);

        String trimmedName = updateDTO.getName().trim();
        ensureNameAvailable(trimmedName, id);

        // Update the fields
        entity.setName(trimmedName);
        entity.setDescription(updateDTO.getDescription());
        entity.setCiteAs(updateDTO.getCiteAs());
        updatePublicationStatus(entity, updateDTO.getStatus());
        entity.setPurpose(updateDTO.getPurpose());
        entity.setCopyright(updateDTO.getCopyright());
        entity.setCopyrightLabel(updateDTO.getCopyrightLabel());
        entity.setKeycloakId(keycloakId);
        cohortCriterionBO.replaceCriteria(entity, updateDTO.getCriteria());
        ao.persist(entity);
        return mapper.entityToDetailDto(entity);
    }

    @Transactional
    public CohortDeletionAcceptedDTO scheduleDeletion(Long id, String keycloakId) {
        validateBeforeDelete(id, keycloakId);
        if (!DELETIONS_IN_PROGRESS.add(id)) {
            throw new WebApplicationException(
                    "Cohort deletion is already in progress.",
                    Response.Status.CONFLICT);
        }

        Infrastructure.getDefaultExecutor().execute(() -> {
            try {
                self.get().executeDeletion(id);
            } catch (Exception e) {
                Log.errorf(e, "Cohort deletion failed for cohort %d", id);
            } finally {
                DELETIONS_IN_PROGRESS.remove(id);
            }
        });

        return new CohortDeletionAcceptedDTO(id, "IN_PROGRESS");
    }

    @ActivateRequestContext
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void executeDeletion(Long cohortId) {
        int batchSize = config.cohort().deleteBatchSize();

        self.get().deleteConnectors(cohortId);
        self.get().deletePatientToolRuns(cohortId);
        self.get().deleteAuditForCohort(cohortId);

        if (config.cohort().deleteTraceability()) {
            deletePatientsInBatches(cohortId, batchSize);
        } else {
            detachPatientsInBatches(cohortId, batchSize);
        }

        self.get().deleteSchemaArtifacts(cohortId);
        self.get().deleteCohortEntity(cohortId);
        Log.infof("Finished deletion for cohort %d", cohortId);
    }

    private void deletePatientsInBatches(Long cohortId, int batchSize) {
        while (true) {
            List<Long> patientIds = patientAO.findIdsByCohortId(cohortId, 0, batchSize);
            if (patientIds.isEmpty()) {
                return;
            }
            self.get().deletePatientsBatch(patientIds);
        }
    }

    private void detachPatientsInBatches(Long cohortId, int batchSize) {
        while (true) {
            List<Long> patientIds = patientAO.findIdsByCohortId(cohortId, 0, batchSize);
            if (patientIds.isEmpty()) {
                return;
            }
            self.get().detachPatientsBatch(cohortId, patientIds);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void deleteConnectors(Long cohortId) {
        connectorBO.deleteAllForCohort(cohortId);
        ao.flush();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void deletePatientToolRuns(Long cohortId) {
        patientToolRunBO.deleteAllForCohort(cohortId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void deleteAuditForCohort(Long cohortId) {
        patientDataAuditAO.deleteAllAuditEntriesForCohort(cohortId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void deletePatientsBatch(List<Long> patientIds) {
        patientAO.deleteByIds(patientIds);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void detachPatientsBatch(Long cohortId, List<Long> patientIds) {
        List<PatientEntity> patients = patientAO.findAllByIds(patientIds);
        for (PatientEntity patient : patients) {
            if (patient.getCohort() == null || !cohortId.equals(patient.getCohort().getId())) {
                continue;
            }
            patient.getDataEntries().clear();
        }
        ao.flush();
        patientDataAuditAO.deleteAllAuditEntriesForPatients(patientIds);
        for (PatientEntity patient : patients) {
            if (patient.getCohort() == null || !cohortId.equals(patient.getCohort().getId())) {
                continue;
            }
            patient.setCohort(null);
        }
        ao.flush();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void deleteSchemaArtifacts(Long cohortId) {
        cohortQueryAbilityAO.deleteAll(cohortId);
        schemaNodeAO.deleteReferencedDataTypeIds(cohortId);
        schemaNodeAO.deleteReferencedOntologyIds(cohortId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void deleteCohortEntity(Long cohortId) {
        CohortEntity entity = ao.findById(cohortId);
        if (entity != null) {
            ao.delete(entity);
        }
    }

    private void validateBeforeDelete(Long id, String keycloakId) {
        assertNotDeleting(id);
        CohortEntity entity = ao.findById(id);
        if (entity == null) {
            throw new NotFoundException("Cohort not found");
        }
        cohortAuthBO.checkForDelete(entity, keycloakId);

        if (connectorRunAO.hasRunningProcess(id)) {
            throw new WebApplicationException(
                    "Cohort deletion is not allowed while imports are running.",
                    Response.Status.CONFLICT);
        }
    }

    private void assertNotDeleting(Long cohortId) {
        if (isDeletionInProgress(cohortId)) {
            throw new WebApplicationException(
                    "Cohort deletion is in progress.",
                    Response.Status.CONFLICT);
        }
    }

    private void updatePublicationStatus(CohortEntity entity, PublicationStatus status) {
        PublicationStatus previousStatus = entity.getStatus();
        entity.setStatus(status);
        if (status == PublicationStatus.ACTIVE) {
            if (previousStatus != PublicationStatus.ACTIVE || entity.getApprovalDate() == null) {
                entity.setApprovalDate(LocalDate.now());
            }
            return;
        }
        entity.setApprovalDate(null);
    }
}
