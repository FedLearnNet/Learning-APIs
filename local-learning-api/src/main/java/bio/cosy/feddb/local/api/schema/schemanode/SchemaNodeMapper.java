package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapper;
import bio.cosy.feddb.local.api.schema.ontology.OntologyDTO;
import bio.cosy.feddb.local.api.schema.ontology.OntologyEntity;
import bio.cosy.feddb.local.api.schema.ontology.OntologyMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Mapper(config = QuarkusMappingConfig.class)
public interface SchemaNodeMapper extends BaseMapper<LocalSchemaNodeDTO, SchemaNodeEntity> {

    OntologyMapper ontologyMapper = Mappers.getMapper(OntologyMapper.class);
    DataTypeEntityMapper dataTypeEntityMapper = Mappers.getMapper(DataTypeEntityMapper.class);

    @Mappings({
            @Mapping(target = "ontology", expression = "java(ontologyMapper.entityToDto(entity.getOntology()))"),
            @Mapping(target = "dataType", expression = "java(dataTypeEntityMapper.entityToDto(entity.getDataType()))"),
            @Mapping(target = "parentId", source = "entity.parent.id"),
            @Mapping(target = "childrenIds", source = "entity", qualifiedByName = "entityChildrenToIds"),
    })
    LocalSchemaNodeDTO entityToDto(SchemaNodeEntity entity);

    @Mappings({
            @Mapping(target = "parentId", source = "entity.parent.id"),
    })
    SimpleLocalSchemaNodeDTO entityToSimpleDto(SchemaNodeEntity entity);


    @Mappings({
            @Mapping(source = "dataType", target = "dataType", qualifiedByName = "shallowDataType"),
            @Mapping(source = "ontology", target = "ontology", qualifiedByName = "shallowOntology"),
            @Mapping(target = "cohort", ignore = true), // not set in the DTO!
            @Mapping(target = "parent", ignore = true),
            @Mapping(target = "children", ignore = true),
            @Mapping(target = "depth", ignore = true),
            // hiearchy is handled by the caller!
            @Mapping(target = "createdBy", ignore = true),
            @Mapping(target = "dataEntities", ignore = true),
    })
    SchemaNodeEntity dtoToEntity(LocalSchemaNodeDTO dto);


    // Mapping from LocalSchemaInfoDTO to SchemaNodeEntity (for root schema node)
    @Mappings({
            @Mapping(target = "dataEntities", ignore = true), // Ignored as children are filled by the caller
            @Mapping(target = "id", ignore = true), // Auto-generated
            @Mapping(target = "type", expression = "java(bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType.ROOT)"), // Root node type
            @Mapping(target = "depth", constant = "0L"), // Root depth is 0
            @Mapping(target = "parent", ignore = true), // Root has no parent
            @Mapping(target = "children", ignore = true), // Will be set separately
            @Mapping(target = "cohort", ignore = true), // Will be initialized separately
            @Mapping(target = "dataType", ignore = true), // Root typically has no data type
            @Mapping(target = "ontology", ignore = true), // Root typically has no ontology
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "createdBy", ignore = true)
    })
    SchemaNodeEntity localSchemaToRootEntity(LocalSchemaRootNodeDTO remoteLoadedSchema);

    @Mappings({
            @Mapping(target = "ontology", expression = "java(ontologyMapper.entityToDto(entity.getOntology()))"),
            @Mapping(target = "dataType", expression = "java(dataTypeEntityMapper.entityToDto(entity.getDataType()))"),
            @Mapping(target = "parentId", source = "entity.parent.id"),
            @Mapping(target = "childrenIds", source = "entity", qualifiedByName = "entityChildrenToIds"),
            @Mapping(target = "childNodes", source = "entity", qualifiedByName = "mapChildren"),
    })
    LocalSchemaNodeNestedDTO entityToNested(SchemaNodeEntity entity);

    @Mappings({
            @Mapping(target = "childNodes", source = "entity", qualifiedByName = "mapChildren"),
            @Mapping(target = "globalVersion", source = "entity.version"),
    })
    LocalSchemaRootNodeDTO entityToRoot(SchemaNodeEntity entity);

    @Named("entityChildrenToIds")
    default Set<Long> entityChildrenToIds(SchemaNodeEntity entities) {
        if (entities == null) {
            return new HashSet<>();
        }
        return entities.getChildren().stream().map(SchemaNodeEntity::getId).collect(Collectors.toSet());
    }

    @Named("mapChildren")
    default Set<LocalSchemaNodeNestedDTO> mapChildren(SchemaNodeEntity entity) {
        if (entity.getChildren() == null) return new HashSet<>();
        return entity.getChildren().stream().map(this::entityToNested).collect(Collectors.toSet());
    }

    @Named("shallowDataType")
    default DataTypeEntity shallowDataType(DataTypeDTO dto) {
        if (dto == null) return null;
        DataTypeEntity e = new DataTypeEntity();
        e.setId(dto.getId());
        return e;
    }

    @Named("shallowOntology")
    default OntologyEntity shallowOntology(OntologyDTO dto) {
        if (dto == null) return null;
        OntologyEntity e = new OntologyEntity();
        e.setId(dto.getId());
        return e;
    }

}
