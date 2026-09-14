package bio.cosy.feddb.core.api.workflow.base.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseMapper;
import io.quarkus.logging.Log;

import java.util.Optional;

public abstract class BaseWorkflowStepBO<Dto extends BaseWorkflowStepDTO, Entity extends BaseWorkflowStepEntity, Ao extends BaseWorkflowStepAO<Entity>,
        Mapper extends BaseMapper<Dto, Entity>> extends BaseBo<Dto, Entity, Ao, Mapper> {


    public Dto updateStatus(Dto dto) {
        Log.infof("Updating status for experiment step ID: %d", dto.getId());
        Optional<Entity> step = ao.findByIdOptional(dto.getId());
        if (step.isEmpty()) {
            Log.errorf("Step entity not found for ID: %d", dto.getId());
            throw new IllegalArgumentException("Step with id " + dto.getId() + " not found");
        }

        // Extract key information from DTO
        if (step.get().getWorkflowNode() == null) {
            Log.errorf("Workflow node not found for workflow ID: %d", dto.getWorkflowNodeId());
            throw new IllegalArgumentException("Workflow node not found for workflow ID: " + dto.getWorkflowNodeId());
        }

        // Update status and handle any errors
        if (dto.getLastError() != null) {
            Log.warnf("Error detected for step ID %d: %s", dto.getId(), dto.getLastError());
            persistStepError(dto.getId(), dto.getLastError());
        } else {
            // If the app supports progress, update it
            if (dto.getProgress() != null) {
                ao.updateStatusTransactional(dto.getId(), dto.getStepStatus(), dto.getProgress());
            } else {
                ao.updateStatusTransactional(dto.getId(), dto.getStepStatus());
            }
        }

        return getById(dto.getId());
    }

    public Optional<Entity> findByWorkflowNodeId(Long experimentId, String workflowNodeId) {
        return ao.findByWorkflowNodeId(experimentId, workflowNodeId);
    }

    public Optional<Entity> findEntityByIdOptional(Long stepId) {
        return ao.findByIdOptional(stepId);
    }

    public void persistStep(Long stepId, RunStatusTypes status) {
        ao.updateStatusTransactional(stepId, status);
    }

    public void persistStep(Long stepId, RunStatusTypes status, String containerId) {
        ao.updateStatusTransactional(stepId, status, containerId);
    }

    public void persistStepError(Long stepId, String lastError) {
        ao.updateErrorTransactional(stepId, lastError);
    }

    public void setAppHasError(Entity step, String errorMessage) {
        ao.updateErrorTransactional(step.getId(), errorMessage);
    }
}
