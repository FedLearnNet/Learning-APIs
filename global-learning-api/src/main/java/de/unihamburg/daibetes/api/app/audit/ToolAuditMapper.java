package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.ToolAuditDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ToolAuditMapper extends BaseMapper<ToolAuditDTO, ToolAuditEntity> {
    @Mappings({

            @Mapping(target = "appVersionId", source = "appVersion.id")
    })
    ToolAuditDTO entityToDto(ToolAuditEntity entity);

    @Mappings({

            @Mapping(target = "appVersion.id", source = "appVersionId")
    })
    ToolAuditEntity dtoToEntity(ToolAuditDTO dto);
}
