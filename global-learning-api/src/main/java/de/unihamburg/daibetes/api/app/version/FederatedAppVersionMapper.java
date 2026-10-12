package de.unihamburg.daibetes.api.app.version;

import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.ToolAuditDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.audit.ToolAuditEntity;
import de.unihamburg.daibetes.api.app.audit.ToolAuditMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppVersionMapper extends BaseMapper<FederatedAppVersionDTO, FederatedAppVersionEntity> {

    ToolAuditMapper auditMapper = Mappers.getMapper(ToolAuditMapper.class);

    @Mappings({
            @Mapping(target = "appVersion", source = "versionFormatted"),
            @Mapping(target = "federatedAppId", source = "federatedApp.id"),
            @Mapping(target = "appConfig", ignore = true),
            @Mapping(target = "audits", source = "audits", qualifiedByName = "mapAudits")
    })
    FederatedAppVersionDTO entityToDto(FederatedAppVersionEntity entity);

    @Mappings({
            @Mapping(target = "federatedApp.id", source = "federatedAppId"),
            @Mapping(target = "majorVersion", source = "appVersion"),
            @Mapping(target = "minorVersion", source = "appVersion"),
            @Mapping(target = "patchVersion", source = "appVersion"),
            @Mapping(target = "testRuns", ignore = true),
            @Mapping(target = "hyperParamConfig", ignore = true),
            @Mapping(target = "inputConfig", ignore = true),
            @Mapping(target = "outputConfig", ignore = true),
            @Mapping(target = "audits", ignore = true)
    })
    FederatedAppVersionEntity dtoToEntity(FederatedAppVersionDTO sourceCode);


    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "federatedApp", ignore = true),
            @Mapping(target = "majorVersion", source = "appVersion"),
            @Mapping(target = "minorVersion", source = "appVersion"),
            @Mapping(target = "patchVersion", source = "appVersion"),
            @Mapping(target = "testRuns", ignore = true),
            @Mapping(target = "hyperParamConfig", ignore = true),
            @Mapping(target = "inputConfig", ignore = true),
            @Mapping(target = "outputConfig", ignore = true),
            @Mapping(target = "audits", ignore = true)
    })
    void updateEntityFromDto(FederatedAppVersionDTO dto, @MappingTarget FederatedAppVersionEntity entity);

    FederatedAppVersionEntity entityToEntity(FederatedAppVersionEntity entity);

    @Named("mapAudits")
    default List<ToolAuditDTO> mapAudits(Set<ToolAuditEntity> audits) {
        if (audits == null) {
            return null;
        }
        return audits.stream()
                .map(auditMapper::entityToDto)
                .toList();
    }
}
