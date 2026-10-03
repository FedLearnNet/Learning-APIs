package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerAO;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import bio.cosy.feddb.local.api.pipeline.PipelineEntity;
import bio.cosy.feddb.local.api.pipeline.PipelineMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import jakarta.inject.Inject;
import org.mapstruct.*;

import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public abstract class CodeTransformerMapper implements BaseMapper<CodeTransformerDTO, CodeTransformerEntity> {

    @Inject
    PipelineMapper pipelineMapper;

    @Inject
    ConnectorTransformerAO connectorTransformerAO;

    @Mapping(target = "pipeline", source = "pipelines", qualifiedByName = "latestPipeline")
    @Mapping(target = "usages", source = "imageName", qualifiedByName = "usages")
    public abstract CodeTransformerDTO entityToDto(CodeTransformerEntity entity);

    @Mapping(target = "pipelines", ignore = true)
    public abstract CodeTransformerEntity dtoToEntity(CodeTransformerDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "codeHash", ignore = true),
            @Mapping(target = "imageName", ignore = true),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "pipelines", ignore = true),
            @Mapping(target = "inputKeys", source = "generated.inputKeys"),
            @Mapping(target = "returnKeys", source = "generated.returnKeys"),
            @Mapping(target = "files", source = "generated.files"),
    })
    public abstract void updateFromSave(CodeTransformerSaveDTO request, CodeTransformerSaveResultDTO generated,
                                        @MappingTarget CodeTransformerEntity entity);

    @Named("latestPipeline")
    public PipelineDTO latestPipeline(List<PipelineEntity> pipelines) {
        return pipelines == null || pipelines.isEmpty() ? null : pipelineMapper.entityToDto(pipelines.getFirst());
    }

    @Named("usages")
    public List<CodeTransformerUsageDTO> usages(String imageName) {
        return imageName == null ? List.of() : connectorTransformerAO.connectorsUsing(imageName).stream()
                .map(connector -> new CodeTransformerUsageDTO(connector.getId(), connector.getName(), connector.getCohort().getId()))
                .toList();
    }
}
