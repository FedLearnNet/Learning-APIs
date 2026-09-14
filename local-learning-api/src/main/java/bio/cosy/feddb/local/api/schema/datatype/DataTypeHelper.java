package bio.cosy.feddb.local.api.schema.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;

public class DataTypeHelper {

    public static DataTypeNodeDTO toDataTypeNodeDTO(DataTypeEntity entity) {
        // TODO: Instead we could use the same
        // objects than the core/datamodeler package use instead of using
        // completely own objects for the schema
        // for now, this works however
        DataTypeNodeDTO dto = new DataTypeNodeDTO();
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        if (entity.getAllowedValues() != null) {
            dto.setOptions(entity.getAllowedValues().stream()
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.toList()));
        }
        if (entity.getValidations() != null) {
            dto.setValidations(new java.util.ArrayList<>(entity.getValidations()));
        }
        dto.setAllowNullValues(entity.getAllowNullValues());
        dto.setIsRequired(entity.getIsRequired());
        return dto;
    }
}
