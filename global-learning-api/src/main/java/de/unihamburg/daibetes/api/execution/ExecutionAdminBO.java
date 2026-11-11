package de.unihamburg.daibetes.api.execution;

import bio.cosy.feddb.core.api.execution.BaseExecutionAdminBO;
import bio.cosy.feddb.core.api.execution.ExecutionRunSummaryDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunDTO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionAdminBO extends BaseExecutionAdminBO<ExecutionRunEntity, ExecutionRunStepEntity, ExecutionAttemptEntity,
        ExecutionRunAO, ExecutionRunStepAO, ExecutionAttemptAO> {

    @Inject
    ExecutionAdminMapper adminMapper;

    @Inject
    ExecutionRunDetailMapper detailMapper;

    @Override
    protected ExecutionRunSummaryDTO mapRunEntityToSummaryDTO(ExecutionRunEntity run) {
        return adminMapper.mapRunEntityToSummaryDTO(run);
    }

    @Override
    protected BaseExecutionRunDTO mapRunEntityToDetailDTO(ExecutionRunEntity run) {
        return detailMapper.mapRunEntityToDetailDTO(run, false);
    }

    @Override
    protected BaseExecutionRunStepDTO mapStepEntityToDetailDTO(ExecutionRunStepEntity step) {
        return detailMapper.mapStepEntityToDetailDTO(step, false);
    }

    @Override
    protected BaseExecutionAttemptDTO mapAttemptEntityToDetailDTO(ExecutionAttemptEntity attempt) {
        return detailMapper.mapAttemptEntityToDetailDTO(attempt, true);
    }
}
