package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import bio.cosy.feddb.local.api.workflow.WorkflowBO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedLearningProjectBO extends BaseBo<ProjectDetailDTO, FederatedLearningProjectEntity, FederatedLearningProjectAO, FederatedLearningProjectMapper> {

    @Inject
    WorkflowBO workflowBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    FederatedLearningExperimentBO experimentBO;

    @Inject
    QueryAO queryAO;

    @Inject
    PatientDataExportBO exportBO;

    public PagedResponse<FederatedLearningProjectDTO> list(Page page) {
        List<FederatedLearningProjectEntity> foundEntities = ao.list(page);
        if (foundEntities.isEmpty()) {
            return new PagedResponse<>(page.index, page.size);
        }
        List<FederatedLearningProjectDTO> found = foundEntities.stream()
                .map(e -> {
                    final ProjectDetailDTO project = mapper.entityToDto(e);
                    final FederatedLearningExperimentDTO experiment = experimentBO.getMapper().entityToDto(e.getExperiment());
                    final Long workflowId = project.getWorkflowId();
                    return new FederatedLearningProjectDTO(project, experiment, workflowId);
                }).toList();

        return new PagedResponse<>(found, page.index, page.size);
    }

    public List<SearchResultDTO<ProjectDetailDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(entity -> hasCohortSearchAccess(getCohortIds(entity), keycloakId))
                .map(entity -> {
                    ProjectDetailDTO dto = mapper.entityToDto(entity);
                    String title = dto.getName();
                    int score = SearchScoreUtil.score(query, title, dto.getDescription(),
                            dto.getGlobalUniqueQueryId(), String.valueOf(dto.getStatus()));
                    return SearchScoreUtil.toResult(SearchResultType.TRAINING, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    private Set<Long> getCohortIds(FederatedLearningProjectEntity project) {
        if (project.getRequest() != null && project.getRequest().getPatients() != null) {
            return project.getRequest().getPatients().stream()
                    .map(patientLearning -> patientLearning.getPatient().getCohort().getId())
                    .collect(Collectors.toSet());
        }
        if (project.getQuery() != null && project.getQuery().getPatients() != null) {
            return project.getQuery().getPatients().stream()
                    .map(queryPatient -> queryPatient.getPatient().getCohort().getId())
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    public FederatedLearningProjectDTO getProjectDetailById(Long id) {
        return ao.findByIdOptional(id).map(e -> {
            final ProjectDetailDTO project = mapper.entityToDto(e);
            final FederatedLearningExperimentDTO experiment = experimentBO.entityToDTO(e.getExperiment());
            final Long workflowId = project.getWorkflowId();
            return new FederatedLearningProjectDTO(project, experiment, workflowId);
        }).orElseThrow(() -> new NotFoundException("Project with id " + id + " not found"));
    }

    public Set<Long> getCohortIds(Long id) {
        FederatedLearningProjectEntity project = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Project with id " + id + " not found"));
        if (project.getRequest() != null && project.getRequest().getPatients() != null) {
            return project.getRequest().getPatients().stream()
                    .map(patientLearning -> patientLearning.getPatient().getCohort().getId())
                    .collect(Collectors.toSet());
        }
        if (project.getQuery() != null && project.getQuery().getPatients() != null) {
            return project.getQuery().getPatients().stream()
                    .map(queryPatient -> queryPatient.getPatient().getCohort().getId())
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    @Transactional
    public Optional<ProjectDetailDTO> findByGlobalUniqueExperimentIdOptionalTransactional(String id) {
        return ao.findByRequestIdOptional(id).map(mapper::entityToDto);
    }

    @Transactional
    public Optional<ProjectDetailDTO> updateProjectStatusTransactional(FederatedLearningRequestDTO requestLearning) {
        return ao.findByRequestIdOptional(requestLearning.getGlobalFLExperimentUniqueId()).map(mapper::entityToDto);
    }

    public FederatedLearningProjectEntity createForRequest(Long queryId, FederatedLearningRequestEntity request, ProjectDetailDTO projectVersion, WorkflowDTO workflow) {
        FederatedLearningProjectEntity entity = mapper.dtoToEntity(projectVersion);
        entity.setId(null);
        entity.setQuery(queryAO.findById(queryId));
        entity.setRequest(request);
        entity.setWorkflow(workflowBO.createForProject(workflow));
        ao.persist(entity);
        return entity;
    }

    @Transactional
    public void setProjectAsCoordinatedTransactional(Long id) {
        boolean updated = ao.updateCoordinatorTransactional(id, true);
        if (!updated) {
            throw new NotFoundException("Project with id " + id + " not found");
        }
    }

    public Path exportForLearning(Long projectId){
        FederatedLearningProjectEntity project = ao.findByIdOptional(projectId)
                .orElseThrow(() -> new NotFoundException("Project with id " + projectId + " not found"));
        Long requestId = project.getRequest().getId();
        Long queryId = project.getQuery().getId();
        return exportBO.exportDataForLearning(project.getExportConfig(), queryId, requestId);
    }

}
