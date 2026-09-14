package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerResponseDTO;
import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectFederatedExperimentStepMapper extends BaseMapper<ProjectFederatedExperimentStepDTO, ProjectFederatedExperimentStepEntity> {

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "metrics", ignore = true),
    })
    ProjectFederatedExperimentStepDTO entityToDto(ProjectFederatedExperimentStepEntity entity);


    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "messages", ignore = true),
            @Mapping(target = "workflowNode.id", source = "workflowNodeId")
    })
    ProjectFederatedExperimentStepEntity dtoToEntity(ProjectFederatedExperimentStepDTO dto);

    @Mappings({

            @Mapping(target = "appVersion", source = "appVersion"),
            @Mapping(target = "coordinator", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "key", ignore = true),
            @Mapping(target = "maxNumClients", expression = "java(entity.getClientIds().size())"),
            @Mapping(target = "orderClientIds", source = "entity.clientIds")
    })
    FederatedLearningRelayInfoDTO responseToRelayInfo(CreateFLLearningRelayServerResponseDTO entity, RelayServerAppVersions appVersion);

}
