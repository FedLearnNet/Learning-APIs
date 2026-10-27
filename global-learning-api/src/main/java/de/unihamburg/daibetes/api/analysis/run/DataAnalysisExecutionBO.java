package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.orchestrator.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.analysis.DataAnalysisAO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunBO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.LinkedHashMap;

@ApplicationScoped
public class DataAnalysisExecutionBO {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    DataAnalysisAO dataAnalysisAO;

    @Inject
    ExecutionRunBO executionBO;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    DataAnalysisFileBO dataAnalysisFileBO;

    @Inject
    DataAnalysisRunResultBO resultBO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    DataAnalysisRunResultMapper runResultMapper;

    @Transactional
    public Long startTransactional(Long dataAnalysisId, DataAnalysisCreatePredictionDTO data, String keycloakId) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }
        DataAnalysisEntity analysis = dataAnalysisBO.findEntityById(dataAnalysisId, keycloakId);

        LinkedHashMap<String, Object> inputs;
        if (data.getInputs() == null) {
            inputs = new LinkedHashMap<>();
        } else {
            inputs = data.getInputs();
            dataAnalysisFileBO.requireOwnFiles(inputs, keycloakId);
        }

        ExecutionStartDTO start = new ExecutionStartDTO();
        start.setPurpose(ExecutionPurpose.ANALYSIS);
        ExecutionRunEntity run;
        if (data.getWorkflowId() != null) {
            run = workflowExecutionBO.startWorkflowTransactional(start, data.getWorkflowId(), inputs, keycloakId);
        } else {
            run = executionBO.startSingle(start, data, inputs, keycloakId);
        }
        run.setKeycloakId(keycloakId);

        // The run owns the link, the analysis only lists it.
        run.setDataAnalysis(analysis);
        analysis.getRuns().add(run);
        dataAnalysisBO.invalidateLlmSummary(analysis.getId());

        return run.getId();
    }

    @Transactional
    public void uploadOutputTransactional(Long attemptId, AppRunUploadData upload) {
        ExecutionAttemptEntity attempt = attemptAO.findByIdOptional(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt " + attemptId + " not found"));
        ExecutionRunEntity run = attempt.getStep().getRun();
        attempt.setResult(json(upload.getFieldsNullsafe()));
        dataAnalysisAO.findByRunId(run.getId()).ifPresentOrElse(
                analysis -> dataAnalysisFileBO.storeAttemptOutputs(attempt, analysis, run.getKeycloakId(), upload.getFilesNullsafe()),
                () -> Log.warnf("Run %d belongs to no data analysis, its output files are not kept", run.getId()));

        FinishRunDTO finish = new FinishRunDTO();
        finish.setMeta(upload.getMeta());
        lifecycle.finish(attemptId, finish);
    }

    @Transactional
    public DataAnalysisRunDTO findRunTransactional(Long dataAnalysisId, Long runId, String keycloakId) {
        requireOwnRun(dataAnalysisId, runId, keycloakId);
        return resultBO.findRunByIdTransactional(runId, keycloakId, true);
    }

    @Transactional
    public DataAnalysisRunDTO stopTransactional(Long dataAnalysisId, Long runId, String keycloakId) {
        requireOwnRun(dataAnalysisId, runId, keycloakId);
        ExecutionRunEntity run = workflowExecutionBO.stopTransactional(runId);
        return runResultMapper.mapRunEntityToResultDTO(run, false);
    }

    @Transactional
    public void deleteTransactional(Long dataAnalysisId, Long runId, String keycloakId) {
        requireOwnRun(dataAnalysisId, runId, keycloakId);
        delete(runId);
    }

    @Transactional
    public void deleteRunsOfSubModelTransactional(Long subModelId) {
        stepAO.listRunIdsForSubModel(subModelId).forEach(this::delete);
    }

    private void delete(Long runId) {
        runAO.findByIdOptional(runId).ifPresent(run ->
                dataAnalysisAO.findByRunId(runId).ifPresent(analysis -> analysis.getRuns().remove(run)));
        workflowExecutionBO.deleteTransactional(runId);
    }

    private void requireOwnRun(Long dataAnalysisId, Long runId, String keycloakId) {
        dataAnalysisBO.findEntityById(dataAnalysisId, keycloakId);
        runAO.findByIdOptional(runId)
                .filter(run -> keycloakId.equals(run.getKeycloakId()))
                .orElseThrow(() -> new NotFoundException("Data analysis run not found"));
    }

    private String json(LinkedHashMap<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            Log.warnf(e, "Could not store the uploaded result");
            return null;
        }
    }
}
