package de.unihamburg.daibetes.api.analysis.worklfow;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisWorkflowRunMapper extends BaseMapper<DataAnalysisWorkflowRunDTO, DataAnalysisWorkflowRunEntity> {

    DataAnalysisWorkflowRunStepMapper stepMapper = Mappers.getMapper(DataAnalysisWorkflowRunStepMapper.class);

    @Mappings({
            @Mapping(target = "currentWorkflowNodeId", source = "currentWorkflowNode.id"),
            @Mapping(target = "workflowId", source = "workflow.id"),
            @Mapping(target = "dataAnalysisId", source = "dataAnalysis.id"),
            @Mapping(target = "steps", expression = "java(stepMapper.entitiesToDtos(entity.getSteps()))"),
    })
    DataAnalysisWorkflowRunDTO entityToDto(DataAnalysisWorkflowRunEntity entity);


    @Mappings({
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "currentWorkflowNode", source = "currentWorkflowNodeId", qualifiedByName = "mapCurrentStep"),
            @Mapping(target = "workflow.id", source = "workflowId"),
            @Mapping(target = "dataAnalysis.id", source = "dataAnalysisId"),
            @Mapping(target = "keycloakId", ignore = true)
    })
    DataAnalysisWorkflowRunEntity dtoToEntity(DataAnalysisWorkflowRunDTO dto);


    @Named("mapCurrentStep")
    default DataAnalysisWorkflowRunStepEntity mapCurrentStep(Long currentWorkflowNodeId) {
        if (currentWorkflowNodeId == null) {
            return null;
        }
        DataAnalysisWorkflowRunStepEntity stepEntity = new DataAnalysisWorkflowRunStepEntity();
        stepEntity.setId(currentWorkflowNodeId);
        return stepEntity;
    }
}
