package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningExperimentMapper extends BaseMapper<FederatedLearningExperimentDTO, FederatedLearningExperimentEntity> {

    @Mappings({
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "currentWorkflowNodeId", source = "currentWorkflowNode.id"),
            @Mapping(target = "groupId", ignore = true),
            @Mapping(target = "workflowId", source = "project.workflow.id"),
            @Mapping(target = "workflowVersion", source = "project.workflow.version")
    })
    FederatedLearningExperimentDTO entityToDto(FederatedLearningExperimentEntity entity);

    @Mappings({
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "project.id", source = "projectId"),
            @Mapping(target = "currentWorkflowNode", source = "currentWorkflowNodeId", qualifiedByName = "mapCurrentStep"),
    })
    FederatedLearningExperimentEntity dtoToEntity(FederatedLearningExperimentDTO dto);

    @Named("mapCurrentStep")
    default FederatedLearningExperimentStepEntity mapCurrentStep(Long currentWorkflowNodeId) {
        if (currentWorkflowNodeId == null) {
            return null;
        }
        FederatedLearningExperimentStepEntity stepEntity = new FederatedLearningExperimentStepEntity();
        stepEntity.setId(currentWorkflowNodeId);
        return stepEntity;
    }


    @Mappings({
            @Mapping(target = "appKey", source = "dto.id"),
            @Mapping(target = "clientId", source = "dto.id"),
            @Mapping(target = "clientKey", source = "dto.key"),
            @Mapping(target = "runId", source = "runId")
    })
    ControllerStartLearningRequestDTO relayToController(FederatedLearningRelayInfoDTO dto, String runId);
}
