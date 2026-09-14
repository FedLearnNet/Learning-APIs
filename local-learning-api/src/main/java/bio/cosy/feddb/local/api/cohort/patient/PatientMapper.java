package bio.cosy.feddb.local.api.cohort.patient;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryHelper;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryEntity;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeHelper;
import bio.cosy.feddb.local.api.schema.datatype.ValidationBO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import org.apache.commons.lang3.NotImplementedException;
import org.mapstruct.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public abstract class PatientMapper {
    @Inject
    SchemaNodeAO schemaNodeAO;

    @Inject
    ValidationBO validationBO;

    @Inject
    FLNetClientConfig config;

    // All methods go from DTO to a new Entity and from an existing Entity to a DTO.
    // DTO to Entity mappings ALWAYS create new entities - BOs need to handle merging logic.
    // The relationships between the entities (metadataentry to dataentry to patient) are
    // handled in the AfterMapping methods.
    // Furthermore, the schemanodes are connected as they are needed to set the value of
    // the entity when converting from DTO to Entity.
    // This means that some validation logic is contained in the mapper.
    // This includes receiving the schema node, checking if it exists, checking if the value
    // fits the corresponding datatype as well as it's validation rules.
    // However the BO must set the relationships to other entities (cohort, learnings, queries)
    // and manage the dataentryversioning.
    // The DTO to Entity methods throw:
    // IllegalStateException if e.g. the schema is broken (internal server error, should not happen)
    // IllegalArgumentException if the DTO is not valid (e.g. schema node ID is missing)

    // Entity to DTO mappings
    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "dataEntries", source = "dataEntries", qualifiedByName = "mapDataEntitiesToDtos")
    })
    public abstract PatientDTO entityToDto(PatientEntity entity);

    @Mappings({
            @Mapping(target = "schemaNodeId", source = "schemaNode.id"),
            @Mapping(target = "value", source = ".", qualifiedByName = "getValueFromDataEntryEntity"),
            @Mapping(target = "metaData", source = "metaDataEntries", qualifiedByName = "mapMetaDataEntitiesToDtos"),
    })
    public abstract PatientDataEntryDTO entityToDto(PatientDataEntryEntity entity);

    @Mappings({
            @Mapping(target = "schemaNodeId", source = "schemaNode.id"),
            @Mapping(target = "value", source = ".", qualifiedByName = "getValueFromMetaEntryEntity"),
    })
    public abstract MetaPatientDataEntryDTO entityToDto(MetaPatientDataEntryEntity entity);


    // DTO to Entity mappings
    @Mappings({
            @Mapping(target = "dataEntries", ignore = true),
            // Set in AfterMapping
            @Mapping(target = "cohort", ignore = true),
            // Set in the BO
            @Mapping(target = "learnings", ignore = true),
            @Mapping(target = "queries", ignore = true),
            // learnings and query are read only and not contained in the DTO!
            // must be overwritten in the BO for e.g. Updates
    })
    public abstract PatientEntity dtoToEntity(PatientDTO dto);

    // Simple method for MapStruct expression - relationships set outside
    @Mappings({
            @Mapping(target = "patient", ignore = true),
            @Mapping(
                    target = "visitTimestamp",
                    expression = "java(normalizeVisitTimestamp(dto.getVisitTimestamp(), dto.getVisitTimestampFormat()))"
            ),
            // Set in AfterMapping of PatientDataDTO to PatientMetaEntity
            @Mapping(target = "schemaNode", ignore = true),
            @Mapping(target = "metaDataEntries", ignore = true),
            @Mapping(target = "valueInt", ignore = true),
            @Mapping(target = "valueFloat", ignore = true),
            @Mapping(target = "valueBoolean", ignore = true),
            @Mapping(target = "valueString", ignore = true),
            @Mapping(target = "valueBlob", ignore = true),
            @Mapping(target = "valueDate", ignore = true),
            @Mapping(target = "valueDateTime", ignore = true),
            // Set in AfterMapping
            @Mapping(target = "importSchemaGroupId", ignore = true)
            // NOT given in the DTO but automatically set on creations/updates by the BO
    })
    public abstract PatientDataEntryEntity dtoToEntity(PatientDataEntryDTO dto);

    @Mappings({
            @Mapping(target = "patientDataEntry", ignore = true),
            // Set in AfterMapping of PatientDataEntryEntity to MetaPatientDataEntryEntity
            @Mapping(target = "schemaNode", ignore = true),
            @Mapping(target = "valueInt", ignore = true),
            @Mapping(target = "valueFloat", ignore = true),
            @Mapping(target = "valueBoolean", ignore = true),
            @Mapping(target = "valueString", ignore = true),
            @Mapping(target = "valueBlob", ignore = true),
            @Mapping(target = "valueDate", ignore = true),
            @Mapping(target = "valueDateTime", ignore = true)
            // Set in AfterMapping
    })  // Basically this just maps the attributes from the baseDTO/baseEntity
    public abstract MetaPatientDataEntryEntity dtoToEntity(MetaPatientDataEntryDTO dto);

    // Named Helper methods for Entity to DTO expressions
    @Named("mapDataEntitiesToDtos")
    protected Set<PatientDataEntryDTO> mapDataEntitiesToDtos(Set<PatientDataEntryEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new java.util.HashSet<>();
        }
        return entities.stream()
                .map(this::entityToDto)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Named("getValueFromDataEntryEntity")
    public static Object getValue(PatientDataEntryEntity entity) {
        return PatientDataEntryHelper.getValue(entity, entity.getSchemaNode().getDataType().getType());
    }

    @Named("mapMetaDataEntitiesToDtos")
    protected Set<MetaPatientDataEntryDTO> mapMetaDataEntitiesToDtos(Set<MetaPatientDataEntryEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new java.util.HashSet<>();
        }
        return entities.stream()
                .map(this::entityToDto)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Named("getValueFromMetaEntryEntity")
    public static Object getValue(MetaPatientDataEntryEntity entity) {
        DataTypes dataType = entity.getSchemaNode().getDataType().getType();
        try {
            switch (dataType) {
                case DataTypes.STRING:
                case DataTypes.CATEGORICAL:
                    return entity.getValueString();
                case DataTypes.INT:
                    return entity.getValueInt();
                case DataTypes.FLOAT:
                    return entity.getValueFloat();
                case DataTypes.BOOLEAN:
                    return entity.getValueBoolean();
                case DataTypes.FILE:
                    return entity.getValueBlob();
                case DataTypes.DATE:
                    return entity.getValueDate();
                case DataTypes.DATE_TIME:
                    return entity.getValueDateTime();
                default:
                    throw new IllegalStateException("Unknown data type: " + dataType);
            }
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Value type does not match meta attribute type: " + dataType, e);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date(time) format for meta attribute type: " + dataType);
        }
    }

    /**
     * Normalizes a visit timestamp string into an {@link Instant}.
     *
     * If a {@code visitTimestampFormat} is provided, the timestamp is parsed using the
     * specified {@link java.time.format.DateTimeFormatter} pattern and converted to an Instant
     * assuming UTC.
     *
     * If no format is provided, the timestamp is parsed using the default validation logic
     * from {@code ValidationBO}, which attempts to interpret the value as a DATE.
     *
     * Note: This method returns {@code null} if the input {@code visitTimestamp} is {@code null}.
     *
     * @param visitTimestamp the visit timestamp string to normalize (may be null)
     * @param visitTimestampFormat optional format pattern used to parse the timestamp (may be null)
     * @return the normalized {@link Instant}, or {@code null} if input is null
     */
    @Named("visitTimestampNormalization")
    public Instant normalizeVisitTimestamp(String visitTimestamp,
                                           String visitTimestampFormat) {
        if (visitTimestamp == null) {
            return null;
        }

        if (visitTimestampFormat != null) {

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(visitTimestampFormat);

            boolean containsTime =
                    visitTimestampFormat.contains("H") ||
                            visitTimestampFormat.contains("m") ||
                            visitTimestampFormat.contains("s");

            if (containsTime) {
                return LocalDateTime.parse(visitTimestamp, formatter)
                        .toInstant(ZoneOffset.UTC);
            }

            return LocalDate.parse(visitTimestamp, formatter)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
        }

        // Use the validationBOs date/datetime logic for the visitTimestamp to
        // use the same logic for validating the visit timestamp as
        // for validating date/datetime attributes
        try {
            return validationBO.convertToInstant(visitTimestamp, DataTypes.DATE_TIME);
        } catch (IllegalArgumentException e) {
            // might just be a date and not an instant. Then we use the date parsing logic
            // to get an instant at the start of the day
            return validationBO.convertToInstant(visitTimestamp, DataTypes.DATE);
        }
    }
    // AfterMapping methods needed for the dto to entity mappings as in comparison
    // to the entity to dto mappings we have to setup the relationships
    // between the meta patient, data entry and meta data entry
    @AfterMapping
    protected void finishPatientDataDTOToEntity(PatientDTO dto, @MappingTarget PatientEntity entity) {
        // Set the data entries
        if (dto.getDataEntries() != null) {
            for (PatientDataEntryDTO entryDTO : dto.getDataEntries()) {
                PatientDataEntryEntity entryEntity = dtoToEntity(entryDTO);
                entryEntity.setPatient(entity);
                entity.getDataEntries().add(entryEntity);
            }
        }
    }

    @AfterMapping
    protected void finishPatientDataEntryDTOToEntity(PatientDataEntryDTO dto, @MappingTarget PatientDataEntryEntity entity) {
        // Patient is set by the PatientDataDTO mapper or the BO
        // SchemaNode is needed or we cannot set the value
        if (dto.getSchemaNodeId() == null) {
            throw new IllegalArgumentException("Schema node ID must be provided for data entries");
        }
        SchemaNodeEntity schemaNode = schemaNodeAO.findById(dto.getSchemaNodeId());
        if (schemaNode == null) {
            throw new IllegalArgumentException("Schema node with ID " + dto.getSchemaNodeId() + " does not exist");
        }
        if (schemaNode.getType() != SchemaNodeType.ATOMIC_ATTRIBUTE && schemaNode.getType() != SchemaNodeType.LIST_ATTRIBUTE) {
            throw new IllegalArgumentException("Schema node with ID " + schemaNode.getId() +
                    " is not an attribute node (type: " + schemaNode.getType() + ")");
        }
        entity.setSchemaNode(schemaNode);

        // Set the value based on the schema node type
        setValue(entity, dto.getValue());

        // Set meta data entries
        if (dto.getMetaData() != null) {
            for (MetaPatientDataEntryDTO metaDTO : dto.getMetaData()) {
                MetaPatientDataEntryEntity metaEntity = dtoToEntity(metaDTO);
                metaEntity.setPatientDataEntry(entity);
                entity.getMetaDataEntries().add(metaEntity);
            }
        }
    }

    @AfterMapping
    protected void finishMetaPatientDataEntryDTOToEntity(MetaPatientDataEntryDTO dto, @MappingTarget MetaPatientDataEntryEntity entity) {
        // Patient data entry is set by the PatientDataEntryEntity mapper
        // SchemaNode is needed or we cannot set the value
        if (dto.getSchemaNodeId() == null) {
            throw new IllegalArgumentException("Schema node ID must be provided for meta data entries");
        }
        SchemaNodeEntity schemaNode = schemaNodeAO.findById(dto.getSchemaNodeId());
        if (schemaNode == null) {
            throw new IllegalArgumentException("Schema node with ID " + dto.getSchemaNodeId() + " does not exist");
        }
        entity.setSchemaNode(schemaNode);
        setValue(entity, dto.getValue());
    }

    // Helpers for dto to entity mappings
    public void setValue(PatientDataEntryEntity entity, Object value) {
        SchemaNodeEntity schemaNode = entity.getSchemaNode();
        DataTypeEntity dataTypeEntity = schemaNode.getDataType();
        // Permit empty values (null or "") when the data type opts into allowNullValues.
        // The entry is then stored without any value_* column populated and flagged via
        // isNullValue=true so downstream queries can distinguish "explicitly null" from
        // "never set". Required-ness is still enforced by validateValue below.
        if (validationBO.valueIsEmpty(value) && dataTypeEntity != null
                && Boolean.TRUE.equals(dataTypeEntity.getAllowNullValues())) {
            entity.setIsNullValue(true);
            validationBO.validateValue(null, DataTypeHelper.toDataTypeNodeDTO(dataTypeEntity));
            return;
        }
        Object normalizedValue = validationBO.normalizeValue(value, DataTypeHelper.toDataTypeNodeDTO(dataTypeEntity));
        if (value != null) {
            // otherwise postgressql default value is null anyways
            try {
                // we just cast, assuming that the normalizeValue method correctly normalized
                switch (schemaNode.getDataType().getType()) {
                    case DataTypes.STRING:
                    case DataTypes.CATEGORICAL:
                        entity.setValueString((String) normalizedValue);
                        break;
                    case DataTypes.INT:
                        entity.setValueInt((Long) normalizedValue);
                        break;
                    case DataTypes.FLOAT:
                        entity.setValueFloat((Float) normalizedValue);
                        break;
                    case DataTypes.BOOLEAN:
                        entity.setValueBoolean((Boolean) normalizedValue);
                        break;
                    case DataTypes.FILE:
                        entity.setValueBlob((String) normalizedValue);
                        break;
                    case DataTypes.DATE:
                        entity.setValueDate((LocalDate) normalizedValue);
                        break;
                    case DataTypes.DATE_TIME:
                        entity.setValueDateTime((Instant) normalizedValue);
                        break;
                    default:
                        Log.error("Could not transform " + normalizedValue + " to type " + schemaNode.getDataType().getType());
                        throw new IllegalStateException("Unknown data type: " + schemaNode.getDataType().getType());
                }
            } catch (ClassCastException e) {
                throw new IllegalStateException("Could not convert the given value to the wanted value type: " + schemaNode.getDataType().getType() + " Error: " + e.getMessage(), e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value cannot be converted to the required type: " + normalizedValue + " to " + schemaNode.getDataType().getType(), e);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date(time) format for schema node type: " + schemaNode.getDataType().getType(), e);
            }
        }
        DataTypeEntity dataType = schemaNode.getDataType();
        validationBO.validateValue(normalizedValue, DataTypeHelper.toDataTypeNodeDTO(dataType));
    }

    public void setValue(MetaPatientDataEntryEntity entity, Object value) {
        DataTypes dataType = entity.getSchemaNode().getDataType().getType();
        Object normalizedValue = validationBO.normalizeValue(value, DataTypeHelper.toDataTypeNodeDTO(entity.getSchemaNode().getDataType()));
        if (value != null) {
            // otherwise postgressql default value is null anyways
            try {
                switch (dataType) {
                    case DataTypes.STRING:
                    case DataTypes.CATEGORICAL:
                        entity.setValueString((String) normalizedValue);
                        break;
                    case DataTypes.INT:
                        entity.setValueInt((Long) normalizedValue);
                        break;
                    case DataTypes.FLOAT:
                        entity.setValueFloat((Float) normalizedValue);
                        break;
                    case DataTypes.BOOLEAN:
                        entity.setValueBoolean((Boolean) normalizedValue);
                        break;
                    case DataTypes.FILE:
                        throw new NotImplementedException("Meta attributes of type FILE are currently not supported");
                    case DataTypes.DATE:
                        entity.setValueDate((LocalDate) normalizedValue);
                        break;
                    case DataTypes.DATE_TIME:
                        entity.setValueDateTime((Instant) normalizedValue);
                        break;
                    default:
                        throw new IllegalStateException("Unknown data type: " + dataType);
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Value type does not match meta attribute type: " + dataType, e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Value cannot be converted to the required type: " + dataType, e);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date(time) format for meta attribute type: " + dataType, e);
            }
        }
        validationBO.validateValue(normalizedValue, DataTypeHelper.toDataTypeNodeDTO(entity.getSchemaNode().getDataType()));
    }


    // Converts an ISO 8601 date string to a LocalDate
    // Validates against calendar adoption system based on configuration
    // Throws IllegalArgumentException if the date falls in the prohibited range
    private LocalDate isoStringToLocalDate(String dateString) {
        LocalDate date = LocalDate.parse(dateString);
        validateCalendarDate(date);
        return date;
    }

    // Converts an ISO 8601 timestamp string to an Instant
    // Validates against calendar adoption system based on configuration
    // Throws IllegalArgumentException if the date falls in the prohibited range
    private Instant isoStringToInstant(String timestampString) {
        Instant instant = Instant.parse(timestampString);
        // Convert to LocalDate for validation (using system default timezone)
        LocalDate date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        validateCalendarDate(date);
        return instant;
    }

    // Validates a date against the configured calendar adoption system
    // Throws IllegalArgumentException if the date falls in the prohibited gap
    private void validateCalendarDate(LocalDate date) {
        String calendarAdoption = config.calendar().gregorian().adoption();
        switch (calendarAdoption.toLowerCase()) {
            case "catholic":
                // Catholic adoption: reject 1582-10-05 to 1582-10-14
                if (date.isAfter(LocalDate.of(1582, 10, 4)) && date.isBefore(LocalDate.of(1582, 10, 15))) {
                    throw new IllegalArgumentException(
                            "Date " + date + " falls in the Catholic calendar gap (1582-10-05 to 1582-10-14). " +
                                    "These dates never existed due to the Gregorian calendar reform."
                    );
                }
                break;
            case "british":
                // British adoption: reject 1752-09-03 to 1752-09-13
                if (date.isAfter(LocalDate.of(1752, 9, 2)) && date.isBefore(LocalDate.of(1752, 9, 14))) {
                    throw new IllegalArgumentException(
                            "Date " + date + " falls in the British calendar gap (1752-09-03 to 1752-09-13). " +
                                    "These dates never existed due to the British Calendar Act of 1752."
                    );
                }
                break;
            case "pragmatic":
                // Pragmatic: allow all dates
                break;
            default:
                throw new IllegalStateException("Unknown calendar adoption setting: " + calendarAdoption +
                        ". Valid values are: catholic, british, pragmatic");
        }
    }
}
