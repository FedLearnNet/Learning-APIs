package bio.cosy.feddb.core.api.execution;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseExecutionAdminBO<
        Run extends BaseExecutionRunEntity<Step>,
        Step extends BaseExecutionRunStepEntity<Run, ?, Attempt>,
        Attempt extends BaseExecutionAttemptEntity<Step, ?>,
        RunAO extends BaseExecutionRunAO<Run>,
        StepAO extends BaseExecutionRunStepAO<Step>,
        AttemptAO extends BaseExecutionAttemptAO<Attempt>> {

    @Inject
    public RunAO runAO;

    @Inject
    public StepAO stepAO;

    @Inject
    public AttemptAO attemptAO;

    protected abstract ExecutionRunSummaryDTO mapRunEntityToSummaryDTO(Run run);

    // With steps and attempts, without messages.
    protected abstract BaseExecutionRunDTO mapRunEntityToDetailDTO(Run run);

    // With attempts, without messages.
    protected abstract BaseExecutionRunStepDTO mapStepEntityToDetailDTO(Step step);

    // With logs, metrics and files.
    protected abstract BaseExecutionAttemptDTO mapAttemptEntityToDetailDTO(Attempt attempt);

    // Newest first; filters left null match all.
    @Transactional
    public PagedResponse<ExecutionRunSummaryDTO> listRunsTransactional(int page, int pageSize, RunStatusTypes status,
                                                                       ExecutionPurpose purpose, ExecutionMode mode,
                                                                       ExecutionLocation location) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        addCondition(conditions, params, "status", status);
        addCondition(conditions, params, "purpose", purpose);
        addCondition(conditions, params, "mode", mode);
        addCondition(conditions, params, "location", location);

        Sort sort = Sort.descending("id");
        PanacheQuery<Run> query = conditions.isEmpty()
                ? runAO.findAll(sort)
                : runAO.find(String.join(" and ", conditions), sort, params);
        List<ExecutionRunSummaryDTO> runs = query.page(Page.of(page, pageSize)).list().stream()
                .map(this::mapRunEntityToSummaryDTO)
                .toList();
        return new PagedResponse<>(runs, page, pageSize, query.count());
    }

    @Transactional
    public BaseExecutionRunDTO findRunTransactional(Long runId) {
        return runAO.findByIdOptional(runId)
                .map(this::mapRunEntityToDetailDTO)
                .orElseThrow(() -> new NotFoundException("Run " + runId + " not found"));
    }

    @Transactional
    public BaseExecutionRunStepDTO findStepTransactional(Long runId, Long stepId) {
        return stepAO.find("id = ?1 and run.id = ?2", stepId, runId).firstResultOptional()
                .map(this::mapStepEntityToDetailDTO)
                .orElseThrow(() -> new NotFoundException("Step " + stepId + " of run " + runId + " not found"));
    }

    @Transactional
    public BaseExecutionAttemptDTO findAttemptTransactional(Long runId, Long stepId, Long attemptId) {
        return attemptAO.find("id = ?1 and step.id = ?2 and step.run.id = ?3", attemptId, stepId, runId).firstResultOptional()
                .map(this::mapAttemptEntityToDetailDTO)
                .orElseThrow(() -> new NotFoundException("Attempt " + attemptId + " of step " + stepId + " not found"));
    }

    private void addCondition(List<String> conditions, Map<String, Object> params, String field, Object value) {
        if (value != null) {
            conditions.add(field + " = :" + field);
            params.put(field, value);
        }
    }
}
