package de.unihamburg.daibetes.execution.federation;

import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionFederationMapper {

    @Mappings({
            @Mapping(target = "pollInterval", source = "config.pollInterval"),
            @Mapping(target = "timeout", source = "config.timeout"),
            @Mapping(target = "maxPolls", source = "config.maxPolls")
    })
    ExecutionFederationDTO entityToDto(ExecutionFederationEntity entity);

    @Mapping(target = "runId", source = "run.id")
    ExecutionParticipantDTO participantToDto(ExecutionParticipantEntity entity);
}
