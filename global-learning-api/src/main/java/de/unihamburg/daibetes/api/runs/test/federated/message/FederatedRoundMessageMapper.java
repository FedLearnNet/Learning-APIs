package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedRoundMessageMapper extends BaseMapper<FederatedRoundMessageDTO, FederatedRoundMessageEntity> {

    @Mapping(target = "federatedRunId", source = "run.id")
    FederatedRoundMessageDTO entityToDto(FederatedRoundMessageEntity entity);

    @Mapping(target = "run.id", source = "federatedRunId")
    FederatedRoundMessageEntity dtoToEntity(FederatedRoundMessageDTO dto);

    @Mappings({
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "federatedRunId", source = "federatedRunId")
    })
    FederatedRoundMessageDTO createDtoToEntity(FederatedRoundMessageCreateDTO create, Long federatedRunId);
}
