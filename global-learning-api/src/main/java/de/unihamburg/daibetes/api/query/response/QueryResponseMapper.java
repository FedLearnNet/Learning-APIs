package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoDTO;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoReducedDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryResponseMapper extends BaseMapper<QueryResponseDTO, QueryResponseEntity> {

    SystemComputingInfoReducedDTO reduce(SystemComputingInfoDTO info);

    // query, connection, count and name are set by QueryResponseBO
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "query", ignore = true)
    @Mapping(target = "connectionId", ignore = true)
    @Mapping(target = "count", ignore = true)
    @Mapping(target = "clientName", ignore = true)
    QueryResponseEntity clientToEntity(QueryClientResponseDTO dto);
}
