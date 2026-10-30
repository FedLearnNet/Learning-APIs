package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryMapper extends BaseMapper<LocalQueryDTO, QueryEntity> {

    @Mappings({
            @Mapping(target = "query", ignore = true),
            @Mapping(target = "enhancedQuery", ignore = true),
            @Mapping(target = "queriedCohortIds", ignore = true)
    })
    LocalQueryDTO entityToDto(QueryEntity entity);

    @Mappings({
            @Mapping(target = "queryString", source = ".", qualifiedByName = "dtoToQueryString"),
            @Mapping(target = "patients", ignore = true)
    })
    QueryEntity dtoToEntity(LocalQueryDTO dto);


    @Mappings({
            @Mapping(target = "schemaNodes", source = "schemaNodes")
    })
    LocalQueryItemDTO localQueryEnhanced(QueryItemDTO query,
                                         List<LocalSchemaNodeDTO> schemaNodes);

    @AfterMapping
    default void readQueryDocument(QueryEntity entity, @MappingTarget LocalQueryDTO dto) {
        dto.setQuery(Lang2QueryDocuments.parse(entity.getQueryString()));
    }

    @Named("dtoToQueryString")
    default String dtoToQueryString(LocalQueryDTO dto) {
        if (dto == null) {
            return "";
        }
        return Lang2QueryDocuments.toQueryString(dto.getQuery());
    }

}
