package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisAO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunServiceImpl.DATA_ANALYSIS_RUN_CHANNEL;

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

    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<DataAnalysisRunDTO> runEmitter;

    @Transactional
    public DataAnalysisRunDTO findRunByIdTransactional(Long runId, String keycloakId, boolean withMessages) {
        ExecutionRunEntity run = runAO.findByIdAndKeycloakId(runId, keycloakId);
        return mapper.mapRunEntityToResultDTO(run, withMessages);
    }

    @Transactional
    public List<DataAnalysisRunDTO> findAnalysisRunsTransactional(Long dataAnalysisId) {
        return dataAnalysisAO.findById(dataAnalysisId)
                .getRuns().stream()
                .map(run -> mapper.mapRunEntityToResultDTO(run, false))
                .toList();
    }

    // Changed analysis runs go to the streams that follow them; a closed stream only misses them.
    void onRunsChanged(@Observes ExecutionRunsChangedEvent event) {
        if (event.getPurpose() != ExecutionPurpose.ANALYSIS) {
            return;
        }
        event.getRunIds().forEach(runId -> runAO.findByIdOptional(runId)
                .map(run -> mapper.mapRunEntityToResultDTO(run, false))
                .ifPresent(run -> {
                    try {
                        runEmitter.send(run);
                    } catch (RuntimeException e) {
                        Log.debug("Analysis run not sent, its stream is closed", e);
                    }
                }));
    }

    @Transactional
    public DataAnalysisResultDTO findResultTransactional(Long runId, String keycloakId) {
        return mapper.mapRunEntityToAnalysisResultDTO(runAO.findByIdAndKeycloakId(runId, keycloakId));
    }


    @Transactional
    public List<DataAnalysisResultDTO> resultsOfAnalysisTransactional(Long dataAnalysisId, boolean flat) {
        return dataAnalysisAO.findById(dataAnalysisId).getRuns().stream()
                .flatMap(run -> flat
                        ? mapper.mapRunEntityToAnalysisResultDTOs(run).stream()
                        : Stream.ofNullable(mapper.mapRunEntityToAnalysisResultDTO(run)))
                .toList();
    }

    @Transactional
    public List<DataAnalysisPredictionDTO> listSingleTransactional(String keycloakId) {
        return stepAO.listSingle(ExecutionPurpose.ANALYSIS, keycloakId)
                .stream().<DataAnalysisPredictionDTO>map(mapper::mapStepEntityToAnalysisResultDTO)
                .toList();
    }

    @Transactional
    public List<DataAnalysisPredictionDTO> listSingleForModelTransactional(String keycloakId, Long modelId) {
        return stepAO.listSingleForModel(ExecutionPurpose.ANALYSIS, keycloakId, modelId).stream()
                .<DataAnalysisPredictionDTO>map(mapper::mapStepEntityToAnalysisResultDTO)
                .toList();
    }

    @Transactional
    public Optional<DataAnalysisResultDTO> resultOfAttemptTransactional(Long attemptId) {
        return attemptAO.findByIdOptional(attemptId).map(attempt -> mapper.mapStepEntityToAnalysisResultDTO(attempt.getStep()));
    }

    /**
     * Swaps file references in the app's input data for links it can download the files from.
     */
    @Transactional
    public StartRunDTO withInputFileUrlsTransactional(StartRunDTO start) {
        if (start.getInputData() != null) {
            start.getInputData().replaceAll((name, value) -> mapper.mapReferenceToFileEntity(value)
                    .map(file -> (Object) fileMapper.entityToDto(file).getFile().getDownloadContentUrlSecret())
                    .orElse(value));
        }
        return start;
    }
}
