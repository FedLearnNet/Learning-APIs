package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisAO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class DataAnalysisRunResultBO {

    // Inputs reference data analysis files as "file://<id>".
    public static final String FILE_PREFIX = "file://";

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    DataAnalysisAO dataAnalysisAO;

    @Inject
    DataAnalysisFileMapper fileMapper;

    @Inject
    DataAnalysisRunResultMapper mapper;

    @Transactional
    public DataAnalysisRunDTO findRunByIdTransactional(Long runId, String keycloakId, boolean withMessages) {
        ExecutionRunEntity run = runAO.findByIdAndKeycloakId(runId, keycloakId);
        return mapper.mapRunEntityToResultDTO(run, withMessages);
    }

    @Transactional
    public List<DataAnalysisRunDTO> findAnalysisRuns(Long dataAnalysisId) {
        return dataAnalysisAO.findById(dataAnalysisId)
                .getRuns().stream()
                .map(run -> mapper.mapRunEntityToResultDTO(run, false))
                .toList();
    }

    @Transactional
    public List<DataAnalysisRunDTO> runsOf(Collection<DataAnalysisRunChange> changes) {
        Map<Long, ExecutionRunEntity> runs = new LinkedHashMap<>();
        changes.forEach(change -> findRunByIdTransactional(change)
                .filter(run -> run.getPurpose() == ExecutionPurpose.ANALYSIS)
                .ifPresent(run -> runs.putIfAbsent(run.getId(), run)));
        return runs.values().stream().map(run -> mapper.mapRunEntityToResultDTO(run, false)).toList();
    }

    private Optional<ExecutionRunEntity> findRunByIdTransactional(DataAnalysisRunChange change) {
        if (change.getAttemptId() != null) {
            return attemptAO.findByIdOptional(change.getAttemptId()).map(attempt -> attempt.getStep().getRun());
        }
        return stepAO.findByIdOptional(change.getStepId()).map(ExecutionRunStepEntity::getRun);
    }

    @Transactional
    public DataAnalysisResultDTO getById(Long runId, String keycloakId) {
        return mapper.mapRunEntityToAnalysisResultDTO(runAO.findByIdAndKeycloakId(runId, keycloakId));
    }


    @Transactional
    public List<DataAnalysisResultDTO> resultsOfAnalysis(Long dataAnalysisId, boolean flat) {
        return dataAnalysisAO.findById(dataAnalysisId).getRuns().stream()
                .flatMap(run -> flat
                        ? mapper.mapRunEntityToAnalysisResultDTOs(run).stream()
                        : Stream.ofNullable(mapper.mapRunEntityToAnalysisResultDTO(run)))
                .toList();
    }

    @Transactional
    public List<DataAnalysisPredictionDTO> listSingle(String keycloakId) {
        return stepAO.listSingle(ExecutionPurpose.ANALYSIS, keycloakId)
                .stream().<DataAnalysisPredictionDTO>map(mapper::mapStepEntityToAnalysisResultDTO)
                .toList();
    }

    @Transactional
    public List<DataAnalysisPredictionDTO> listSingleForModel(String keycloakId, Long modelId) {
        return stepAO.listSingleForModel(ExecutionPurpose.ANALYSIS, keycloakId, modelId).stream()
                .<DataAnalysisPredictionDTO>map(mapper::mapStepEntityToAnalysisResultDTO)
                .toList();
    }

    @Transactional
    public Optional<DataAnalysisResultDTO> resultOfAttempt(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId).map(attempt -> mapper.mapStepEntityToAnalysisResultDTO(attempt.getStep()));
    }

    /**
     * Swaps file references in the app's input data for links it can download the files from.
     */
    @Transactional
    public StartRunDTO withInputFileUrls(StartRunDTO start) {
        if (start.getInputData() != null) {
            start.getInputData().replaceAll((name, value) -> mapper.mapReferenceToFileEntity(value)
                    .map(file -> (Object) fileMapper.entityToDto(file).getFile().getDownloadContentUrlSecret())
                    .orElse(value));
        }
        return start;
    }
}
