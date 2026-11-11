package bio.cosy.feddb.local.api.execution;

import bio.cosy.feddb.core.api.execution.BaseExecutionAdminBO;
import bio.cosy.feddb.core.api.execution.ExecutionRunSummaryDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunDTO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunMapper;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionAdminBO extends BaseExecutionAdminBO<ExecutionRunEntity, ExecutionRunStepEntity, ExecutionAttemptEntity,
        ExecutionRunAO, ExecutionRunStepAO, ExecutionAttemptAO> {

    @Inject
    ExecutionAdminMapper adminMapper;

    @Inject
    ExecutionRunMapper runMapper;

    @Override
    protected ExecutionRunSummaryDTO mapRunEntityToSummaryDTO(ExecutionRunEntity run) {
        return adminMapper.mapRunEntityToSummaryDTO(run);
    }

    @Override
    protected BaseExecutionRunDTO mapRunEntityToDetailDTO(ExecutionRunEntity run) {
        return runMapper.mapRunEntityToDTO(run, false);
    }

    @Override
    protected BaseExecutionRunStepDTO mapStepEntityToDetailDTO(ExecutionRunStepEntity step) {
        return runMapper.mapStepEntityToDetailDTO(step, false);
    }

    @Override
    protected BaseExecutionAttemptDTO mapAttemptEntityToDetailDTO(ExecutionAttemptEntity attempt) {
        return runMapper.mapAttemptEntityToDTO(attempt, true);
    }
}
