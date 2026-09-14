package de.unihamburg.daibetes.api.project.experiment.federated.metrics.response;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface RunMetricsResponseMapper extends BaseMapper<RunMetricsResponseDTO, RunMetricsResponseEntity> {

    @Mapping(target = "requestId", source = "request.id")
    RunMetricsResponseDTO entityToDto(RunMetricsResponseEntity entity);

    @Mapping(target = "request.id", source = "requestId")
    RunMetricsResponseEntity dtoToEntity(RunMetricsResponseDTO dto);
}
