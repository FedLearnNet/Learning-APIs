package de.unihamburg.daibetes.api.project.experiment.local;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentBO;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.base.BaseFileStoreAO;
import bio.cosy.feddb.core.services.orch.WorkflowOrchestrator;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.project.experiment.local.data.ProjectLocalExperimentStepDataBO;
import de.unihamburg.daibetes.api.project.experiment.local.run.ProjectLocalExperimentBroadcastBO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepDTO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepDetailDTO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipAO;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.unihamburg.daibetes.api.project.experiment.ProjectExperimentServiceImpl.EXPERIMENT_LOCAL_CHANNEL;

@ApplicationScoped
public class ProjectLocalExperimentBO extends BaseWorkflowExperimentBO<ProjectLocalExperimentDTO,
        ProjectLocalExperimentStepEntity,
        ProjectLocalExperimentStepDTO,
        ProjectLocalExperimentEntity,
        ProjectLocalExperimentAO,
        ProjectLocalExperimentMapper,
        ProjectLocalExperimentStepBO> {

    @Inject
    ProjectMembershipAO projectMembershipAO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @Inject
    ProjectLocalExperimentStepDataBO stepResultBO;

    @Inject
    @Channel(EXPERIMENT_LOCAL_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<ProjectLocalExperimentDTO> infoEmitter;

    @Inject
    ProjectLocalExperimentBroadcastBO projectLocalExperimentBroadcastBO;

    @Inject
    BaseFileStoreAO fileStoreAO;

    public List<ProjectLocalExperimentDTO> getAllByProject(Long id, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                id, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }

        return mapper.entitiesToDtos(ao.findByProjectId(id));
    }

    public ProjectLocalExperimentDTO getById(Long projectId, Long id, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }

        return getById(id);
    }

    public ProjectLocalExperimentDTO startLocal(Long projectId, Long id, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }

        startLearning(id);
        return getById(id);
    }

    public ProjectLocalExperimentDTO stopLocal(Long projectId, Long id, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }
        return stopLearning(id);
    }

    public ProjectLocalExperimentStepDetailDTO getStepById(Long projectId, Long experimentId, Long stepId, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }

        ProjectLocalExperimentStepDetailDTO step = stepBo.getDetailById(stepId);
        if (step.getExperimentId().equals(experimentId)) {
            return step;
        } else {
            throw new NotFoundException("Step not found for this experiment");
        }
    }

    public ProjectLocalExperimentDTO getTestById(Long projectId, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }
        Optional<ProjectLocalExperimentEntity> testExperiment = ao.getTestByProjectId(projectId);
        if (testExperiment.isEmpty()) {
            throw new NotFoundException("No test experiment found for this project");
        }
        return mapper.entityToDto(testExperiment.get());
    }

    public ProjectLocalExperimentDTO createTestAndStart(Long projectId, String keycloakId) {
        ProjectLocalExperimentDTO createdDto = create(projectId, keycloakId);
        startLearning(createdDto.getId());
        return createdDto;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProjectLocalExperimentDTO create(Long projectId, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }


        Log.infof("Deleting existing test experiment for project ID %d before creating a new one.", projectId);
        Optional<ProjectLocalExperimentEntity> testExperiment = ao.getTestByProjectId(projectId);
        testExperiment.ifPresent(projectLocalExperimentEntity -> ao.delete(projectLocalExperimentEntity));
        ao.flush();

        ProjectLocalExperimentDTO createDto = new ProjectLocalExperimentDTO();
        createDto.setProjectId(projectId);
        createDto.setExperimentStatus(ProjectStatus.READY);
        createDto.setTestRun(true);
        ProjectLocalExperimentEntity entity = mapper.dtoToEntity(createDto);
        ao.persist(entity);
        ao.flush();
        entity.setSteps(stepBo.createSteps(entity));
        return mapper.entityToDto(entity);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ProjectLocalExperimentDTO create(Long projectId, CreateProjectLocalExperimentDTO dto, String keycloakId) {
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                projectId, keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }

        ProjectLocalExperimentDTO createDto = mapper.createDtoToDto(dto);
        createDto.setProjectId(projectId);
        createDto.setExperimentStatus(ProjectStatus.READY);
        createDto.setTestRun(false);
        ProjectLocalExperimentEntity entity = mapper.dtoToEntity(createDto);
        ao.persist(entity);
        ao.flush();
        entity.setSteps(stepBo.createSteps(entity));
        return mapper.entityToDto(entity);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void startLearning(Long experimentId) {
        ProjectLocalExperimentEntity experimentEntity = ao.findById(experimentId);
        boolean success = handleInitLearning(experimentId);
        if (success) {
            uploadData(experimentEntity);
        }
    }


    private void uploadData(ProjectLocalExperimentEntity entity) {
        try {
            Log.info("Starting data export for learning workflow ID: " + entity.getId());
            Map<String, Path> data = getDataV1(entity);
            if (data.isEmpty()) {
                Log.warn("No data found to upload for learning.");
                return;
            }
            Log.info("Uploading data for learning: " + data.size() + " files found.");
            for (Map.Entry<String, Path> entry : data.entrySet()) {
                Log.info("Uploading file: " + entry.getKey() + " to workflow ID: " + entity.getId() + ", node: " + entity.getCurrentWorkflowNode().getId());
                workflowOrchestratorBO.uploadFilesToVolume(entity.getId(), entity.getCurrentWorkflowNode().getId(), entry.getValue(), entry.getKey());
            }
        } catch (Exception e) {
            Log.error("Error during data export for learning experiment ID: " + entity.getId() + ", node: " + entity.getId(), e);
        }
    }

    @Override
    public BaseFileEntity getInputFile(ProjectLocalExperimentEntity entity) {
        return entity.getProject().getFile();
    }

    @Override
    public Path getInputPath(ProjectLocalExperimentEntity entity) {
        FileEntity file = entity.getProject().getFile();
        if (file != null) {
            return fileStoreAO.loadFile(file.getLargeObjectId()).toPath();
        }
        return null;
    }

    @Override
    public void onPreFinishStep(ProjectLocalExperimentStepDTO step) {
        stepResultBO.saveResults(step.getId());
    }

    @Override
    public void onFinishStep(Long experimentId, boolean experimentFinish) {
        startLearning(experimentId);
    }

    @Override
    protected WorkflowOrchestrator getWorkflowOrchestrator() {
        return workflowOrchestratorBO;
    }

    @Override
    protected WorkflowDTO getWorkflowDTO(Long experimentId) {
        ProjectLocalExperimentEntity experimentEntity = ao.findById(experimentId);
        return getWorkflowDTO(experimentEntity);
    }

    @Override
    protected Scope getToolApiKeyScope() {
        return Scope.LOCAL_EXPERIMENT_RUN;
    }

    @Override
    protected String getWSBaseUrl() {
        return "project/experiment/local";
    }

    @Override
    protected void emitChange(ProjectLocalExperimentDTO dto) {
        try {
            infoEmitter.send(dto);
            Log.debug("SSE event sent: " + dto);
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }

    @Override
    protected void startStep(ProjectLocalExperimentStepEntity entity) {
        //TODO IMPLEMENT
        boolean isTraining = false;
        projectLocalExperimentBroadcastBO.startStep(getStartup(entity.getId()), isTraining);
    }

    @Override
    protected WorkflowDTO getWorkflowDTO(ProjectLocalExperimentEntity experimentEntity) {
        return workflowBO.entityToDto(experimentEntity.getProject().getWorkflow());
    }

    @Override
    protected void saveFirstStepData(ProjectLocalExperimentEntity entity, ProjectLocalExperimentStepEntity stepEntity) {
        FileEntity file = entity.getProject().getFile();
        stepResultBO.createForStep(file, stepEntity);
    }
}
