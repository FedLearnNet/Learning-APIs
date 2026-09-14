package bio.cosy.feddb.local.api.schema.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface DataTypeEntityMapper extends BaseMapper<DataTypeDTO, DataTypeEntity> {

    @Mappings({
            @Mapping(target = "ontologyIds", ignore = true),
            @Mapping(target = "schemaIds", ignore = true),
            @Mapping(target = "description", source = "desc"),
            @Mapping(target = "formType", source = "entity", qualifiedByName = "entityDataTypeToFormType"),
            @Mapping(target = "globalId", source = "globalDataTypeId"),
    })
    DataTypeDTO entityToDto(DataTypeEntity entity);


    @Mappings({
            @Mapping(target = "schemaNodes", ignore = true),
            @Mapping(target = "desc", source = "description"),
            @Mapping(target = "globalDataTypeId", source = "globalId"),
    })
    DataTypeEntity dtoToEntity(DataTypeDTO dto);


    @ValueMapping(target = "NUMBER", source = "INT")
    @ValueMapping(target = "NUMBER", source = "FLOAT")
    @ValueMapping(target = "RADIO", source = "BOOLEAN")
    @ValueMapping(target = "SELECT", source = "CATEGORICAL")
    @ValueMapping(target = "FILE", source = "FILE")
    @ValueMapping(target = "TEXT", source = "STRING")
    @ValueMapping(target = "DATE", source = "DATE")
    @ValueMapping(target = "DATE_TIME", source = "DATE_TIME")
    DatatypeFormTypeEnum dataTypeToFormType(DataTypes dataType);

    @Named("entityDataTypeToFormType")
    default DatatypeFormTypeEnum entityDataTypeToFormType(DataTypeEntity entity) {
        if (entity == null || entity.getType() == null) {
            return null;
        }
        return dataTypeToFormType(entity.getType());
    }

    @AfterMapping
    default void setAllowedValuesMappingFromOptions(DataTypeDTO dto, @MappingTarget DataTypeEntity entity) {
        Set<DataTypeOptionsDTO> options = (dto == null) ? null : dto.getOptions();
        if (options == null || options.isEmpty()) {
            entity.setAllowedValues(new HashSet<>());
            entity.setMapping(new HashMap<>());
            return;
        }
        Set<Object> allowed = entity.getAllowedValues() == null
                ? new HashSet<>()
                : new HashSet<>(entity.getAllowedValues());
        Map<String, String> map = entity.getMapping() == null
                ? new HashMap<>()
                : new HashMap<>(entity.getMapping());

        for (DataTypeOptionsDTO option : options) {
            if (option == null) {
                throw new IllegalArgumentException("Option value and label must not be null");
            }
            allowed.add(option.getValue());
            map.put(option.getValue().toString(), option.getLabel());
        }

        entity.setAllowedValues(allowed);
        entity.setMapping(map);
    }


}
