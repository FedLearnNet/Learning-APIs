package de.unihamburg.daibetes.api.query.statistics;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface DataStatisticsResponseMapper extends BaseMapper<DataStatisticsResponseDTO, DataStatisticsResponseEntity> {

    @Mapping(target = "queryId", source = "query.id")
    DataStatisticsResponseDTO entityToDto(DataStatisticsResponseEntity entity);

    @Mapping(target = "query.id", source = "queryId")
    DataStatisticsResponseEntity dtoToEntity(DataStatisticsResponseDTO dto);


}
