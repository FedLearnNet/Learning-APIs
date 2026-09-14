package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface ConnectorRunMessagesMapper extends BaseMapper<ConnectorRunMessagesDTO, ConnectorRunMessagesEntity> {


    @Mappings({
            @Mapping(target = "runId", source = "run.id"),
            @Mapping(target = "transformerId", source = "transformer", qualifiedByName = "mapTransformerToId"),
            @Mapping(target = "stepId", source = "step", qualifiedByName = "mapStepToId"),
            @Mapping(target = "workerId", ignore = true)
    })
    ConnectorRunMessagesDTO entityToDto(ConnectorRunMessagesEntity entity);

    @Mapping(target = "step", source = "stepId", qualifiedByName = "mapIdToStep")
    @Mapping(target = "run.id", source = "runId")
    @Mapping(target = "transformer", source = "transformerId", qualifiedByName = "mapIdToTransformer")
    ConnectorRunMessagesEntity dtoToEntity(ConnectorRunMessagesDTO sourceCode);


    @Named("mapStepToId")
    default Long mapStepToId(ConnectorRunStepEntity step) {
        return step != null ? step.getId() : null;
    }


    @Named("mapIdToStep")
    default ConnectorRunStepEntity mapIdToStep(Long stepId) {
        if (stepId == null) {
            return null;
        }

        ConnectorRunStepEntity step = new ConnectorRunStepEntity();
        step.setId(stepId);
        return step;
    }

    @Named("mapTransformerToId")
    default Long mapTransformerToId(ConnectorTransformerEntity step) {
        return step != null ? step.getId() : null;
    }


    @Named("mapIdToTransformer")
    default ConnectorTransformerEntity mapIdToTransformer(Long stepId) {
        if (stepId == null) {
            return null;
        }

        ConnectorTransformerEntity step = new ConnectorTransformerEntity();
        step.setId(stepId);
        return step;
    }

    @Mappings({
            @Mapping(target = "runId", ignore = true),
            @Mapping(target = "severity", ignore = true),
            @Mapping(target = "transformerId", ignore = true),
            @Mapping(target = "stepId", ignore = true)
    })
    ConnectorRunMessagesDTO runDtoToDto(RunMessageDTO sourceCode);

}
