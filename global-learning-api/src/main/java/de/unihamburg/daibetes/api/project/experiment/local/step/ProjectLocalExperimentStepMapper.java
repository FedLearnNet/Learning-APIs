package de.unihamburg.daibetes.api.project.experiment.local.step;

import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectLocalExperimentStepMapper extends BaseMapper<ProjectLocalExperimentStepDTO, ProjectLocalExperimentStepEntity> {
    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id")
    })
    ProjectLocalExperimentStepDTO entityToDto(ProjectLocalExperimentStepEntity entity);

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "logMessages", ignore = true),
            @Mapping(target = "metrics", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true),
            @Mapping(target = "result", ignore = true)
    })
    ProjectLocalExperimentStepDetailDTO entityToDetailDto(ProjectLocalExperimentStepEntity entity);


    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "workflowNode.id", source = "workflowNodeId"),
            @Mapping(target = "inputs", ignore = true),
            @Mapping(target = "results", ignore = true)
    })
    ProjectLocalExperimentStepEntity dtoToEntity(ProjectLocalExperimentStepDTO dto);
}
