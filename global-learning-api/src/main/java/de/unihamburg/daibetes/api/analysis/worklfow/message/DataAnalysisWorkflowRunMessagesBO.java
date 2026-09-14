package de.unihamburg.daibetes.api.analysis.worklfow.message;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionAO;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepAO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import de.unihamburg.daibetes.api.runs.base.message.RunMessageLogMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class DataAnalysisWorkflowRunMessagesBO extends BaseBo<DataAnalysisWorkflowRunMessagesDTO, DataAnalysisWorkflowRunMessagesEntity, DataAnalysisWorkflowRunMessagesAO, DataAnalysisWorkflowRunMessagesMapper> {

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    DataAnalysisWorkflowRunStepAO stepAo;

    @Inject
    DataAnalysisPredictionAO predictionAo;

    public List<DataAnalysisWorkflowRunMessagesDTO> findById(Long projectId, String keycloakId) {
        return mapper.entitiesToDtos(ao.findByStep(projectId));
    }

    public void create(RunMessageLogDTO dto, DataAnalysisRunModesEnum runMode, Long runId) {
        if (runId != null) {
            RunMessageDTO toCreate = logMapper.logDtoToDto(dto);
            DataAnalysisWorkflowRunMessagesDTO dataAnalysisDto = mapper.runDtoToDto(toCreate);
            DataAnalysisWorkflowRunMessagesEntity toPersist = mapper.dtoToEntity(dataAnalysisDto);
            toPersist.setRunMode(runMode);
            if (runMode.equals(DataAnalysisRunModesEnum.PREDICTION)) {
                toPersist.setStep(null);
                DataAnalysisPredictionEntity prediction = predictionAo.findById(runId);
                if (prediction == null) {
                    throw new IllegalArgumentException("Prediction with ID " + runId + " not found.");
                }
                toPersist.setPrediction(prediction);
            } else {
                toPersist.setPrediction(null);
                DataAnalysisWorkflowRunStepEntity step = stepAo.findById(runId);
                if (step == null) {
                    throw new IllegalArgumentException("Step with ID " + runId + " not found.");
                }
                toPersist.setStep(step);
            }
            ao.persist(toPersist);
        }
    }

}
