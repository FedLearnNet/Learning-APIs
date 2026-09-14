package bio.cosy.feddb.local.services.datamodler;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeDetailDTO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeOptionsDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Mapper(config = QuarkusMappingConfig.class)
public interface GlobalSchemaServiceMapper {


    @Mappings({
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "childNodes", ignore = true),
            @Mapping(target = "globalId", source = "id"),
            @Mapping(target = "globalVersion", source = "version"),
    })
    LocalSchemaRootNodeDTO globalHeadRootToLocalRootDto(SchemaNodeDetailDTO globalSchemaNode);

    @Mappings({
            //BaseDTO
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),

            //Schema
            @Mapping(target = "globalId", source = "id", qualifiedByName = "mapUUIDToString"),
            @Mapping(target = "name", source = "name"),
            @Mapping(target = "description", source = "description"),
            @Mapping(target = "type", source = "type"),
            @Mapping(target = "parentId", ignore = true), //Schema internal parentId not in global

            // Ontology (nested)
            @Mapping(target = "ontology.id", ignore = true),
            @Mapping(target = "ontology.createdAt", ignore = true),
            @Mapping(target = "ontology.updatedAt", ignore = true),
            @Mapping(target = "ontology.version", ignore = true),
            @Mapping(target = "ontology.globalId", source = "ontology.id", qualifiedByName = "mapUUIDToString"),
            @Mapping(target = "ontology.name", source = "ontology.names", qualifiedByName = "mapListToString"),
            @Mapping(target = "ontology.description", source = "ontology.description"),
            @Mapping(target = "ontology.rootSource", source = "ontology.auis", qualifiedByName = "mapListToString"),

            // DataType (nested)
            @Mapping(target = "dataType.id", ignore = true),
            @Mapping(target = "dataType.createdAt", ignore = true),
            @Mapping(target = "dataType.updatedAt", ignore = true),
            @Mapping(target = "dataType.version", ignore = true),
            @Mapping(target = "dataType.mapping", ignore = true),
            @Mapping(target = "dataType.allowedValues", ignore = true),
            @Mapping(target = "dataType.formType", ignore = true),
            @Mapping(target = "dataType.globalId", source = "dataType.id", qualifiedByName = "mapUUIDToString"),
            @Mapping(target = "dataType.type", source = "dataType.type"),
            @Mapping(target = "dataType.name", source = "dataType.name"),
            @Mapping(target = "dataType.description", source = "dataType.description"),
            @Mapping(target = "dataType.validations", source = "dataType.validations"),
            @Mapping(target = "dataType.allowNullValues", source = "dataType.allowNullValues", qualifiedByName = "mapBooleanDefaultTrue"),
            @Mapping(target = "dataType.isRequired", source = "dataType.isRequired"),
            @Mapping(target = "dataType.options", source = "dataType.options", qualifiedByName = "mapOptions"),

            // Children
            @Mapping(target = "childNodes", source = "global", qualifiedByName = "mapGlobalToLocalSchemaChildren"),
            @Mapping(target = "childrenIds", ignore = true)
    })
    LocalSchemaNodeNestedDTO globalToLocalSchemaDto(SchemaStructureDTO global);


    @Mappings({
            //BaseDTO
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "type", source = "type"),
            @Mapping(target = "childNodes", source = "globalRootSchemaInfoDTO", qualifiedByName = "mapGlobalToLocalSchemaChildren"),
            @Mapping(target = "globalId", source = "id", qualifiedByName = "mapUUIDToString"),
            @Mapping(target = "globalVersion", source = "version"),
            @Mapping(target = "name", source = "name"),
            @Mapping(target = "description", source = "description"),
    })
    LocalSchemaRootNodeDTO globalInfoToLocalRoot(SchemaStructureDTO globalRootSchemaInfoDTO);

    @Named("mapGlobalToLocalSchemaChildren")
    default Set<LocalSchemaNodeNestedDTO> mapGlobalToLocalSchemaChildren(SchemaStructureDTO global) {
        if (global.getChildren() == null) return new HashSet<>();
        return global.getChildren().stream().map(this::globalToLocalSchemaDto).collect(Collectors.toSet());
    }

    @Named("mapUUIDToString")
    default String mapUUIDToString(UUID id) {
        if (id == null) {
            // Callers problem now, fuck you calling function
            return "";
        }
        return id.toString();
    }

    @Named("mapBooleanDefaultTrue")
    default Boolean mapBooleanDefaultTrue(Boolean value) {
        if (value == null) {
            return true;
        }
        return value;
    }


    @Named("mapListToString")
    default String mapListToString(Set<String> list) {
        // mimimi I cant do nullptrs - filthy compiler
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(", ", list);
    }

    @Named("mapOptions")
    default Set<DataTypeOptionsDTO> mapOptions(List<String> options) {
        if (options == null) {
            // away you go filthy nullptr error, you are hearby banished from this realm
            return new HashSet<>();
        }
        return options.stream()
                .map(DataTypeOptionsDTO::new)
                .collect(Collectors.toSet());
    }
}
