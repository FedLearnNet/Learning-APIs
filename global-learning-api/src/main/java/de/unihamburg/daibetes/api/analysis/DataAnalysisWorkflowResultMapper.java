package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisWorkflowResultMapper {


    @Mappings({
            @Mapping(target = "currentWorkflowStep", ignore = true),
            @Mapping(target = "currentWorkflowStepId", ignore = true),
            @Mapping(target = "maxWorkflowSteps", ignore = true),
            @Mapping(target = "runMode", constant = "PREDICTION"),
            @Mapping(target = "workflowRunId", ignore = true),
            @Mapping(target = "executionOrder", ignore = true)
    })
    DataAnalysisResultDTO predictionToResult(DataAnalysisPredictionDTO entity);

}
