package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryResponseMapper extends BaseMapper<QueryResponseDTO, QueryResponseEntity> {

    @Mapping(target = "globalUniqueQueryId", source = "query.globalUniqueId")
    @Mapping(target = "queryId", source = "query.id")
    QueryResponseDTO entityToDto(QueryResponseEntity entity);

    @Mapping(target = "query.id", source = "queryId")
    QueryResponseEntity dtoToEntity(QueryResponseDTO dto);

    @Mappings({
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "globalUniqueQueryId", source = "globalUniqueQueryId"),
            @Mapping(target = "queryId", ignore = true)
    })
    QueryResponseDTO createDtoToDto(QueryClientResponseDTO dto);

}
