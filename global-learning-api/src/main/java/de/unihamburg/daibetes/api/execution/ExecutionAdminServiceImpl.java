package de.unihamburg.daibetes.api.execution;

import bio.cosy.feddb.core.api.execution.ExecutionAdminService;
import bio.cosy.feddb.core.api.execution.ExecutionRunSummaryDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionAdminServiceImpl implements ExecutionAdminService {

    @Inject
    ExecutionAdminBO bo;

    @Override
    public PagedResponse<ExecutionRunSummaryDTO> listRuns(int page, int pageSize, RunStatusTypes status,
                                                          ExecutionPurpose purpose, ExecutionMode mode,
                                                          ExecutionLocation location) {
        return bo.listRunsTransactional(page, pageSize, status, purpose, mode, location);
    }

    @Override
    public BaseExecutionRunDTO getRun(Long runId) {
        return bo.findRunTransactional(runId);
    }

    @Override
    public BaseExecutionRunStepDTO getStep(Long runId, Long stepId) {
        return bo.findStepTransactional(runId, stepId);
    }

    @Override
    public BaseExecutionAttemptDTO getAttempt(Long runId, Long stepId, Long attemptId) {
        return bo.findAttemptTransactional(runId, stepId, attemptId);
    }
}
