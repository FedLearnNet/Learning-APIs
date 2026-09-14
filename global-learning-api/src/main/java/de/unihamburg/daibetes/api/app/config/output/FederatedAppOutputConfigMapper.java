package de.unihamburg.daibetes.api.app.config.output;

import bio.cosy.feddb.core.api.app.config.ToolOutputConfigDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigNameMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppOutputConfigMapper extends BaseMapper<ToolOutputConfigDTO, FederatedAppOutputConfigEntity> {

    FederatedAppConfigNameMapper nameMapper = Mappers.getMapper(FederatedAppConfigNameMapper.class);

    @Mapping(target = "federatedAppVersion", ignore = true)
    FederatedAppOutputConfigEntity dtoToEntity(ToolOutputConfigDTO dto);

    @Mapping(target = "variableName", expression = "java(nameMapper.sanitizeVariableName(entity.getName()))")
    ToolOutputConfigDTO entityToDto(FederatedAppOutputConfigEntity entity);

}
