package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepBO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.services.controller.ControllerStopLearningRequestDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentAO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentEntity;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataBO;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataDTO;
import bio.cosy.feddb.local.api.learning.project.run.data.StepResultContext;
import bio.cosy.feddb.local.api.learning.project.run.message.FederatedLearningExperimentStepMessageBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import bio.cosy.feddb.local.api.workflow.WorkflowBO;
import bio.cosy.feddb.local.api.workflow.WorkflowEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class FederatedLearningExperimentStepBO
        extends BaseWorkflowStepBO<FederatedLearningExperimentStepDTO,
        FederatedLearningExperimentStepEntity,
        FederatedLearningExperimentStepAO,
        FederatedLearningExperimentStepMapper> {

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    FederatedLearningRequestBO federatedLearningRequestBO;

    @Inject
    FederatedLearningExperimentAO experimentAO;

    @Inject
    FederatedLearningExperimentStepMessageBO stepMessageBO;

    @Inject
    FederatedLearningExperimentStepDataBO stepResultBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    WorkflowNodeBO workflowNodeBO;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    protected static final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();


    public Set<FederatedLearningExperimentStepEntity> createSteps(FederatedLearningExperimentEntity entity) {
        FederatedLearningProjectEntity project = entity.getProject();
        if (project == null) {
            throw new NotFoundException("Project not found for experiment");
        }
        WorkflowEntity workflow = project.getWorkflow();
        if (workflow == null) {
            throw new NotFoundException("Project workflow not found for experiment");
        }
        HashSet<FederatedLearningExperimentStepEntity> steps = workflow.getNodes().stream()
                .map(node -> create(node, entity))
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        return steps;
    }

    private FederatedLearningExperimentStepEntity create(WorkflowNodeEntity node, FederatedLearningExperimentEntity experiment) {
        FederatedLearningExperimentStepEntity step = new FederatedLearningExperimentStepEntity();
        step.setExperiment(experiment);
        step.setWorkflowNode(node);
        step.setStepStatus(RunStatusTypes.PENDING);
        ao.persist(step);
        return step;
    }

    public FederatedLearningExperimentStepDTO mapStepToDto(FederatedLearningExperimentStepEntity entity) {
        return mapper.entityToDto(entity);
    }

    public FederatedLearningExperimentStepEntity getEntityById(Long experimentId, Long stepId) {
        FederatedLearningExperimentStepEntity entity = ao.findByIdOptional(stepId)
                .orElseThrow(() -> new NotFoundException("Step with id " + stepId + " not found for experiment " + experimentId));
        if (!entity.getExperiment().getId().equals(experimentId)) {
            throw new NotFoundException("Step with id " + stepId + " not found for experiment " + experimentId);
        }
        return entity;
    }

    public FederatedLearningExperimentStepDTO getById(Long experimentId, Long stepId) {
        FederatedLearningExperimentStepEntity entity = getEntityById(experimentId, stepId);
        return mapper.entityToDto(entity);
    }

    public FederatedLearningExperimentStepDetailDTO getDetailById(Long experimentId, Long stepId) {
        FederatedLearningExperimentStepEntity entity = getEntityById(experimentId, stepId);
        FederatedLearningExperimentStepDetailDTO detail = mapper.entityToDtoDetail(entity);
        detail.setLogs(stepMessageBO.findLogByStepId(stepId));
        detail.setMetrics(stepMessageBO.findMetricByStepId(stepId));

        List<FederatedLearningExperimentStepDataDTO> dataDTOs = stepResultBO.findByStep(stepId);
        for (FederatedLearningExperimentStepDataDTO data : dataDTOs) {
            if (data.getFile() != null && data.getStepInputId() != null && data.getStepInputId().equals(stepId)) {
                detail.addInputFile(data.getFile());
            }
            if (data.getFile() != null && data.getStepOutputId() != null && data.getStepOutputId().equals(stepId)) {
                detail.addOutputFile(data.getFile());
            }

            if (data.getFile() == null && StringUtils.isNotEmpty(data.getResult()) && StringUtils.isNotEmpty(data.getName())) {
                detail.addResultEntry(data.getName(), data.getResult());
            }
        }

        return detail;
    }


    /**
     * Updates the status of a federated learning experiment step and handles any error conditions.
     *
     * @param dto The data transfer object containing the updated step status and related information
     * @return The updated experiment step DTO after persistence
     * @throws IllegalArgumentException if the step entity with the given ID is not found
     */
    public FederatedLearningExperimentStepDTO updateStatus(FederatedLearningExperimentStepDTO dto) {
        Log.infof("Updating status for experiment step ID: %d", dto.getId());

        ProjectStatus stepStatus = ProjectStatus.fromRunStatusTypes(dto.getStepStatus());
        WorkflowNodeDetailDTO node = workflowNodeBO.getDetailById(dto.getWorkflowNodeId());
        if (node == null) {
            Log.errorf("Workflow node not found for workflow ID: %d", dto.getWorkflowNodeId());
            throw new IllegalArgumentException("Workflow node not found for workflow ID: " + dto.getWorkflowNodeId());
        }
        WorkflowDTO workflow = workflowBO.getForSystem(node.getWorkflowId());
        if (workflow == null) {
            Log.errorf("Workflow not found for workflow ID: %d", node.getWorkflowId());
            throw new IllegalArgumentException("Workflow not found for workflow ID: " + node.getWorkflowId());
        }
        FederatedLearningExperimentStepEntity stepEntity = ao.findById(dto.getId());
        if (stepEntity == null) {
            Log.errorf("Step entity not found for ID: %d", dto.getId());
            throw new IllegalArgumentException("Step with id " + dto.getId() + " not found");
        }

        String globalRequestId = stepEntity.getExperiment().getProject().getRequest().getGlobalFLExperimentUniqueId();
        String randomClinicId = stepEntity.getExperiment().getUniqueRandomClinicId();
        Long experimentId = stepEntity.getExperiment().getId();

        boolean updated = persistStatus(dto);
        if (!updated) {
            Log.infof("Skipping duplicate status update for step ID: %d because it is already terminal", dto.getId());
            return mapper.entityToDto(ao.findById(dto.getId()));
        }

        if (dto.getLastError() != null) {
            Log.warnf("Error detected for request ID %s: %s", globalRequestId, dto.getLastError());
            experimentAO.markExperimentError(experimentId);
            federatedLearningRequestBO.stopLearning(globalRequestId);
        }

        FederatedLearningExperimentStepDTO updatedStep = mapper.entityToDto(ao.findById(dto.getId()));
        if (ProjectStatus.isFinalStatus(stepStatus)) {
            boolean isLastStep = baseWorkflowEngine.isLastStep(workflow, node);
            Log.infof("Final status detected for step ID: %d, is last step: %b", dto.getId(), isLastStep);
            handleFinalState(updatedStep, experimentId, isLastStep, stepStatus, globalRequestId);
            stopControllerRun(dto.getId());
        }

        String currentNodeId = node.getNodeId();
        RunStatusTypes projectStatus = resolveProjectStatus(experimentId);
        websocketSender.sendRunningUpdate(currentNodeId, globalRequestId, projectStatus, stepStatus, randomClinicId);

        Log.infof("Successfully updated status for step ID: %d", dto.getId());
        return updatedStep;
    }

    private boolean persistStatus(FederatedLearningExperimentStepDTO dto) {
        if (dto.getLastError() != null) {
            return ao.updateErrorTransactional(dto.getId(), dto.getLastError());
        }
        if (dto.getProgress() != null) {
            return ao.updateStatusTransactional(dto.getId(), dto.getStepStatus(), dto.getProgress());
        }
        return ao.updateStatusTransactional(dto.getId(), dto.getStepStatus());
    }

    private void handleFinalState(FederatedLearningExperimentStepDTO step, Long experimentId, boolean isLastStep, ProjectStatus stepStatus, String globalUqExperimentId) {
        if (isLastStep && ProjectStatus.FINISHED.equals(stepStatus)) {
            Log.infof("Finishing run for experiment ID: %d, global request ID: %s", experimentId, globalUqExperimentId);
            experimentAO.markExperimentFinish(experimentId);
            federatedLearningRequestBO.finishLearning(globalUqExperimentId);
            workflowOrchestratorBO.cleanup(experimentId);
            return;
        }
        if (ProjectStatus.ERROR.equals(stepStatus)) {
            workflowOrchestratorBO.cleanup(experimentId);
            return;
        }
        if (step.getContainerId() != null) {
            workflowOrchestratorBO.cleanup(step.getContainerId(), false);
        }
    }

    private RunStatusTypes resolveProjectStatus(Long experimentId) {
        return experimentAO.findByIdOptional(experimentId)
                .map(FederatedLearningExperimentEntity::getExperimentStatus)
                .map(experimentStatus ->
                        switch (experimentStatus) {
                            case FINISHED -> RunStatusTypes.FINISHED;
                            case ERROR -> RunStatusTypes.ERROR;
                            case STOPPED, SHUTDOWN -> RunStatusTypes.STOPPED;
                            default -> RunStatusTypes.RUNNING;
                        }
                ).orElse(RunStatusTypes.RUNNING);
    }

    public boolean setRelayInfo(Long id, FederatedLearningRelayInfoDTO relayInfo) {
        return ao.setRelayInfoTransactional(id, relayInfo);
    }

    /**
     * Stores the CSR of the step's controller run and returns what has to be sent to the global server to get
     * it signed.
     */
    @Transactional
    public RelayCertRequest startRelayCertRequest(Long stepId, String csr) {
        if (!ao.startRelayCertRequestTransactional(stepId, csr)) {
            throw new IllegalArgumentException("Step with id " + stepId + " not found");
        }
        // the update above bypasses the persistence context, make sure not to read a stale entity
        FederatedLearningExperimentStepEntity step = ao.findById(stepId);
        ao.getEntityManager().refresh(step);
        return toRelayCertRequest(step);
    }

    @Transactional
    public List<RelayCertRequest> findPendingRelayCerts() {
        return ao.findPendingRelayCerts().stream().map(this::toRelayCertRequest).toList();
    }

    private RelayCertRequest toRelayCertRequest(FederatedLearningExperimentStepEntity step) {
        FederatedLearningExperimentEntity experiment = step.getExperiment();
        return new RelayCertRequest(
                step.getId(),
                experiment.getProject().getRequest().getGlobalFLExperimentUniqueId(),
                experiment.getUniqueRandomClinicId(),
                step.getWorkflowNode().getNodeId(),
                step.getRelayInfo() != null ? step.getRelayInfo().getId() : null,
                step.getRelayCsr(),
                step.getRelayCertAttempts() != null ? step.getRelayCertAttempts() : 0,
                step.getRelayCertRequestedAt()
        );
    }

    /**
     * Stops the controller run of every step of the experiment that still has one.
     */
    public void stopControllerRuns(Long experimentId) {
        for (FederatedLearningExperimentStepEntity step : ao.findWithOpenControllerRun(experimentId)) {
            stopControllerRun(step);
        }
    }

    /**
     * Stops the controller run of the step, if one was started and not stopped yet.
     * Never throws: a controller that cannot be reached must not break the workflow, the run then stays
     * open and is retried the next time the experiment is stopped.
     */
    public void stopControllerRun(Long stepId) {
        FederatedLearningExperimentStepEntity step = ao.findById(stepId);
        if (step != null) {
            stopControllerRun(step);
        }
    }

    private void stopControllerRun(FederatedLearningExperimentStepEntity step) {
        FederatedLearningRelayInfoDTO relay = step.getRelayInfo();
        if (relay == null || step.getRelayCsr() == null || Boolean.TRUE.equals(step.getRelayStopped())) {
            return;
        }
        int status;
        // The app key of a controller run is the relay client id, see FederatedLearningExperimentMapper.relayToController
        try (Response response = controllerService.stopLearning(new ControllerStopLearningRequestDTO(relay.getChannel(), relay.getId()))) {
            status = response.getStatus();
        } catch (WebApplicationException e) {
            status = e.getResponse().getStatus();
        } catch (Exception e) {
            Log.warnf(e, "Could not stop the controller run of step %d", step.getId());
            return;
        }
        // 404: the controller does not know the run (anymore), e.g. after a controller restart
        if (status == Response.Status.OK.getStatusCode() || status == Response.Status.NOT_FOUND.getStatusCode()) {
            ao.setRelayStoppedTransactional(step.getId());
            Log.infof("Stopped controller run of step %d", step.getId());
        } else {
            Log.warnf("Controller answered %d when stopping the run of step %d", status, step.getId());
        }
    }
}
