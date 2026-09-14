package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface RunMetricsRequestMapper extends BaseMapper<RunMetricsRequestDTO, RunMetricsRequestEntity> {

    RunMetricsResponseMapper responseMapper = Mappers.getMapper(RunMetricsResponseMapper.class);

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "projectId", source = "experiment.project.id"),
            @Mapping(target = "responsesReceived", source = "experiment.acceptanceClinicCount"),
            @Mapping(target = "totalParticipants", expression = "java(entity.getResponses() != null ? entity.getResponses().size() : 0)"),
            @Mapping(target = "responses", source = "entity.responses", qualifiedByName = "mapResponses")
    })
    RunMetricsRequestDTO entityToDto(RunMetricsRequestEntity entity);

    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "responses", ignore = true)
    })
    RunMetricsRequestEntity dtoToEntity(RunMetricsRequestDTO dto);

    @Named("mapResponses")
    default List<RunMetricsResponseDTO> mapResponses(Set<RunMetricsResponseEntity> responses) {
        if (responses == null || responses.isEmpty()) {
            return new ArrayList<>();
        }
        return responses.stream()
                .map(responseMapper::entityToDto)
                .toList();
    }
}
