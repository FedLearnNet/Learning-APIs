package bio.cosy.feddb.local.api.schema.datatype;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class DataTypeBO extends BaseBo<DataTypeDTO, DataTypeEntity, DataTypeAO, DataTypeEntityMapper> {

    @Inject
    ValidationBO validationBO;

    public DataTypeEntity fetchOrCreate(DataTypeDTO dto) {

        Optional<DataTypeEntity> optionalExistingDataType = ao.findByGlobalId(dto.getGlobalId());
        if (optionalExistingDataType.isPresent()) {
            // Ontology exists, ensure it matches the global schema node
            DataTypeEntity existingDataType = optionalExistingDataType.get();
            if (!isEqual(dto, existingDataType)) {
                throw new IllegalStateException(
                        "Local datatype does not match global datatype. Local: " + existingDataType +
                                ", Global: " + dto);
            }
            mergeDatatype(dto, existingDataType);
            return existingDataType;
        } else {
            validateDataTypeValidations(dto.getValidations(), dto.getType());
            // Ontology does not exist but the schema needs it, create the ontology
            DataTypeEntity localOntology = mapper.dtoToEntity(dto);
            ao.persist(localOntology);
            return localOntology;
        }
    }

    private boolean isEqual(DataTypeDTO dto, DataTypeEntity entity) {
        // 1. Basic Field Checks
        if (!entity.getType().equals(dto.getType()) ||
            !entity.getName().equals(dto.getName()) ||
            !entity.getDesc().equals(dto.getDescription())) {
            return false;
        }

        // 2. Robust Validation Check (Null == Empty)
        Set<DataTypeValidationDTO> entityValidations = entity.getValidations() == null ? Set.of() : entity.getValidations();
        Set<DataTypeValidationDTO> dtoValidations = dto.getValidations() == null ? Set.of() : dto.getValidations();

        // Ignore the options here as they are merged in another function instead
        return entityValidations.equals(dtoValidations);
    }

    private void mergeDatatype(DataTypeDTO dto, DataTypeEntity entity) {
        Set<DataTypeOptionsDTO> entityOptions = entity.getOptions() == null ? Set.of() : entity.getOptions();
        Set<DataTypeOptionsDTO> dtoOptions = dto.getOptions() == null ? Set.of() : dto.getOptions();

        if (entityOptions.isEmpty() && dtoOptions.isEmpty()) {
            return;
        }

        java.util.Map<Object, String> dtoValueToLabel = new java.util.HashMap<>(dtoOptions.size());
        java.util.Map<String, Object> dtoLabelToValue = new java.util.HashMap<>(dtoOptions.size());
        for (DataTypeOptionsDTO option : dtoOptions) {
            if (option == null || option.getValue() == null || option.getLabel() == null) {
                throw new IllegalStateException("DTO option value and label must not be null");
            }
            String existingLabel = dtoValueToLabel.put(option.getValue(), option.getLabel());
            if (existingLabel != null && !existingLabel.equals(option.getLabel())) {
                throw new IllegalStateException("DTO contains conflicting labels for value: " + option.getValue());
            }
            Object existingValue = dtoLabelToValue.put(option.getLabel(), option.getValue());
            if (existingValue != null && !existingValue.equals(option.getValue())) {
                throw new IllegalStateException("DTO contains conflicting values for label: " + option.getLabel());
            }
        }

        java.util.Map<Object, String> entityValueToLabel = new java.util.HashMap<>(entityOptions.size());
        for (DataTypeOptionsDTO option : entityOptions) {
            if (option == null || option.getValue() == null || option.getLabel() == null) {
                throw new IllegalStateException("Entity option value and label must not be null");
            }
            entityValueToLabel.put(option.getValue(), option.getLabel());

            String dtoLabel = dtoValueToLabel.get(option.getValue());
            if (dtoLabel == null) {
                throw new IllegalStateException("DTO is missing value/label pair for value: " + option.getValue());
            }
            if (!dtoLabel.equals(option.getLabel())) {
                throw new IllegalStateException("DTO label mismatch for value: " + option.getValue());
            }

            Object dtoValue = dtoLabelToValue.get(option.getLabel());
            if (dtoValue == null) {
                throw new IllegalStateException("DTO is missing value/label pair for label: " + option.getLabel());
            }
            if (!dtoValue.equals(option.getValue())) {
                throw new IllegalStateException("DTO value mismatch for label: " + option.getLabel());
            }
        }

        if (dtoOptions.size() == entityOptions.size()) {
            return;
        }

        java.util.Set<DataTypeOptionsDTO> updatedOptions = entity.getOptions() == null
                ? new java.util.HashSet<>()
                : new java.util.HashSet<>(entity.getOptions());
        java.util.Set<Object> updatedAllowedValues = entity.getAllowedValues() == null
                ? new java.util.HashSet<>()
                : new java.util.HashSet<>(entity.getAllowedValues());
        java.util.Map<String, String> updatedMapping = entity.getMapping() == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(entity.getMapping());

        boolean changed = false;
        for (DataTypeOptionsDTO option : dtoOptions) {
            if (!entityValueToLabel.containsKey(option.getValue())) {
                updatedOptions.add(option);
                updatedAllowedValues.add(option.getValue());
                updatedMapping.put(option.getValue().toString(), option.getLabel());
                changed = true;
            }
        }

        if (changed) {
            entity.setOptions(updatedOptions);
            entity.setAllowedValues(updatedAllowedValues);
            entity.setMapping(updatedMapping);
            ao.persist(entity);
        }
    }

    /**
     * Validates that a set of DataTypeValidationDTO objects make sense for a given data type.
     * This method checks if the validations are appropriate for the data type and if the
     * validator values are valid.
     *
     * @param validations The set of validations to validate
     * @param dataType    The data type these validations are for
     * @throws IllegalArgumentException if any validation doesn't make sense for the data type
     */
    public void validateDataTypeValidations(Set<DataTypeValidationDTO> validations, DataTypes dataType) {
        if (validations == null || validations.isEmpty()) {
            return; // No validations to validate
        }

        if (dataType == null) {
            throw new IllegalArgumentException("Data type must not be null when validating validations");
        }

        for (DataTypeValidationDTO validation : validations) {
            if (validation.getName() == null) {
                throw new IllegalArgumentException("Validation name must not be null");
            }

            validationBO.validateValidationForDataType(validation, dataType);
        }
    }


    public void delete(Set<DataTypeEntity> entities) {
        for (DataTypeEntity dataType : entities) {
            try {
                // Check if this datatype is still referenced by any remaining schema nodes
                if (dataType.getSchemaNodes() == null || dataType.getSchemaNodes().isEmpty()) {
                    // Data type is not used by any schema node anymore, so we can remove it
                    ao.delete(dataType);
                }
            } catch (jakarta.persistence.EntityNotFoundException e) {
                // DataType was already deleted, which is fine
            }
        }
    }
}
