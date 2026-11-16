package de.unihamburg.daibetes.agent.audit;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface ToolLlmAuditMapper extends BaseMapper<ToolLlmAuditDTO, ToolLlmAuditEntity> {

    @Mapping(target = "appVersionId", source = "appVersion.id")
    ToolLlmAuditDTO entityToDto(ToolLlmAuditEntity entity);

    @Mapping(target = "appVersion", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "fingerprint", ignore = true)
    ToolLlmAuditEntity dtoToEntity(ToolLlmAuditDTO dto);
}
