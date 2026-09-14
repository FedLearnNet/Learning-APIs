package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.file.FileMapper;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningExperimentStepMapper extends BaseMapper<FederatedLearningExperimentStepDTO, FederatedLearningExperimentStepEntity> {

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "globalRequestId", ignore = true)
    })
    FederatedLearningExperimentStepDTO entityToDto(FederatedLearningExperimentStepEntity entity);

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "logs", ignore = true),
            @Mapping(target = "metrics", ignore = true),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "globalRequestId", ignore = true),
            @Mapping(target = "outputFiles", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "result", ignore = true),
    })
    FederatedLearningExperimentStepDetailDTO entityToDtoDetail(FederatedLearningExperimentStepEntity entity);


    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "containerId", ignore = true),
            @Mapping(target = "results", ignore = true),
            @Mapping(target = "workflowNode.id", source = "workflowNodeId"),
            @Mapping(target = "inputs", ignore = true)
    })
    FederatedLearningExperimentStepEntity dtoToEntity(FederatedLearningExperimentStepDTO dto);


}
