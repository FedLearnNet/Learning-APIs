package de.unihamburg.daibetes.api.project.training.result;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.project.training.result.response.RunResultResponseDTO;
import de.unihamburg.daibetes.api.project.training.result.response.RunResultResponseEntity;
import de.unihamburg.daibetes.api.project.training.result.response.RunResultResponseMapper;
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
public interface RunResultRequestMapper extends BaseMapper<RunResultRequestDTO, RunResultRequestEntity> {

    RunResultResponseMapper responseMapper = Mappers.getMapper(RunResultResponseMapper.class);

    @Mappings({
            @Mapping(target = "experimentId", source = "experiment.id"),
            @Mapping(target = "projectId", source = "experiment.project.id"),
            @Mapping(target = "responsesReceived", source = "experiment.acceptanceClinicCount"),
            @Mapping(target = "totalParticipants", expression = "java(entity.getResponses() != null ? entity.getResponses().size() : 0)"),
            @Mapping(target = "responses", source = "entity.responses", qualifiedByName = "mapResponses")
    })
    RunResultRequestDTO entityToDto(RunResultRequestEntity entity);

    @Mappings({
            @Mapping(target = "experiment.id", source = "experimentId"),
            @Mapping(target = "responses", ignore = true)
    })
    RunResultRequestEntity dtoToEntity(RunResultRequestDTO dto);

    @Named("mapResponses")
    default List<RunResultResponseDTO> mapResponses(Set<RunResultResponseEntity> responses) {
        if (responses == null || responses.isEmpty()) {
            return new ArrayList<>();
        }
        return responses.stream()
                .map(responseMapper::entityToDto)
                .toList();
    }
}
