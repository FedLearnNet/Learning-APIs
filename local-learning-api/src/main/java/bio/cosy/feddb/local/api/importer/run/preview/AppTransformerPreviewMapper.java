package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface AppTransformerPreviewMapper {

    @Mappings({
            @Mapping(target = "stepIndex", ignore = true),
            @Mapping(target = "rowCount", ignore = true),
            @Mapping(target = "finished", ignore = true)
    })
    AppTransformerPreviewStreamDTO dtoToStreamDTO(ConnectorRunStepDTO dto);
}
