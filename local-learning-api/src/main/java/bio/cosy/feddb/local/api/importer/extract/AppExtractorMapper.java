package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface AppExtractorMapper {

    @Mappings({
            @Mapping(target = "cached", ignore = true),
            @Mapping(target = "hash", ignore = true),
            @Mapping(target = "uploadInfo", ignore = true)
    })
    ConnectorExtractorStreamDTO dtoToStreamDTO(ConnectorRunStepDTO dto);

    ConnectorExtractorStreamDTO copy(ConnectorExtractorStreamDTO copy);
}
