package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mapper(config = QuarkusMappingConfig.class, imports = HarmonizationProfileHelper.class)
public interface HarmonizationConnectorMapper {

    @Mapping(target = "name", source = "profile.name")
    @Mapping(target = "dataType", expression = "java(HarmonizationProfileHelper.dataType(profile))")
    @Mapping(target = "staticColumn", source = "staticColumn")
    HarmonizationTargetDTO toTarget(ColumnProfile profile, boolean staticColumn);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", expression = "java(HarmonizationConnectorMapper.targetId(index))")
    @Mapping(target = "type", constant = "ATOMIC_ATTRIBUTE")
    @Mapping(target = "name", source = "target.name")
    @Mapping(target = "path", source = "target.name")
    @Mapping(target = "description", source = "target", qualifiedByName = "schemaNodeDescription")
    @Mapping(target = "dataType", source = "target.dataType")
    LocalSchemaNodeWithPathDTO toSchemaNode(HarmonizationTargetDTO target, int index);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "mode", constant = "FILE")
    @Mapping(target = "fileId", source = "fileId")
    FileUploadSettingsDTO toInput(Long fileId);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "inputConfig", expression = "java(toInput(harmonization.getSourceFileId()))")
    @Mapping(target = "mergeConfig", source = "harmonization.sourceMergeConfig")
    @Mapping(target = "transformer", source = "harmonization.transformer")
    @Mapping(target = "schemaMapping", expression = "java(schemaMapping(harmonization, externalIdColumn))")
    ConnectorDTO toConnector(HarmonizationDTO harmonization, String externalIdColumn);

    static long targetId(int index) {
        return -(index + 1L);
    }

    @Named("schemaNodeDescription")
    default String schemaNodeDescription(HarmonizationTargetDTO target) {
        return "Column '" + target.getName() + "' of the reference file. "
                + Objects.requireNonNullElse(target.getDataType().getDescription(), "");
    }

    default List<ConnectorMappingDTO> schemaMapping(HarmonizationDTO harmonization, String externalIdColumn) {
        List<ConnectorMappingDTO> mappings = new ArrayList<>(
                ConnectorMappingDTO.removePatientIdMapping(harmonization.getMapping(), externalIdColumn));
        mappings.add(ConnectorMappingDTO.getPatientIdMapping(harmonization.getSourceKey(), externalIdColumn));
        return mappings;
    }
}
