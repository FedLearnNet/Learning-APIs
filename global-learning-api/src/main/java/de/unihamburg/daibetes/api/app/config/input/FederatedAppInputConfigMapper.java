package de.unihamburg.daibetes.api.app.config.input;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigNameMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppInputConfigMapper extends BaseMapper<ToolInputConfigDTO, FederatedAppInputConfigEntity> {

    FederatedAppConfigNameMapper nameMapper = Mappers.getMapper(FederatedAppConfigNameMapper.class);

    @Mapping(target = "federatedAppVersion", ignore = true)
    FederatedAppInputConfigEntity dtoToEntity(ToolInputConfigDTO dto);

    @Mapping(target = "variableName", expression = "java(nameMapper.sanitizeVariableName(entity.getName()))")
    ToolInputConfigDTO entityToDto(FederatedAppInputConfigEntity entity);

}
