package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryMapper extends BaseMapper<LocalQueryDTO, QueryEntity> {

    @Mappings({
            @Mapping(target = "enhancedQuery", ignore = true),
            @Mapping(target = "queriedCohortIds", ignore = true)
    })
    LocalQueryDTO entityToDto(QueryEntity entity);

    @Mappings({
            @Mapping(target = "patients", ignore = true)
    })
    QueryEntity dtoToEntity(LocalQueryDTO dto);


    @Mappings({
            @Mapping(target = "schemaNodes", source = "schemaNodes")
    })
    LocalQueryItemDTO localQueryEnhanced(QueryItemDTO query,
                                         List<LocalSchemaNodeDTO> schemaNodes);

}
