package de.unihamburg.daibetes.api.project;

import bio.cosy.feddb.core.api.app.config.*;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.datamodler.datatype.DummyDataRequestDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.api.project.PatientDataPivotJoinField;
import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.services.DataModelerDataTypeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class ExportConfigUtilsBO {

    private static final EnumSet<PatientDataPivotJoinField> DEFAULT_JOIN_FIELDS = EnumSet.of(
            PatientDataPivotJoinField.PATIENT_ID,
            PatientDataPivotJoinField.VISIT_ID
    );

    @Inject
    @RestClient
    DataModelerDataTypeService dataModelerDataTypeService;

    @Inject
    FederatedAppConfigBO federatedAppConfigBO;

    public TabularSchemaDTO createSchema(PatientDataExportConfigDTO config) {
        if (config != null && config.isAppBased()) {
            return resolveAppBasedExportSchema(config);
        }

        if (config == null || !config.isWideFormat()) {
            return createLongFormatSchema(config);
        }

        List<PatientExportFeatureDTO> features = getOrderedFeatures(config);
        return createWideFormatSchema(config, features, findDataTypes(features));
    }

    public Response generateTestCsv(PatientDataExportConfigDTO config, Integer amount) {
        if (config == null || config.getFeatures() == null || config.getFeatures().isEmpty()) {
            throw new BadRequestException("Project does not have export features defined");
        }

        DummyDataRequestDTO request = new DummyDataRequestDTO();
        request.setFeatures(getOrderedFeatures(config));
        request.setAmount(amount == null || amount <= 0 ? 25 : amount);
        request.setWideFormat(config.isWideFormat());
        request.setAsFile(true);

        try (Response generated = dataModelerDataTypeService.generateDummyData(request).await().indefinitely()) {
            String body = generated.readEntity(String.class);
            if (generated.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                return Response.status(generated.getStatus())
                        .entity(body)
                        .type(Optional.ofNullable(generated.getMediaType()).orElse(MediaType.APPLICATION_JSON_TYPE))
                        .build();
            }

            return Response.ok(body)
                    .type("text/csv")
                    .header("Content-Disposition", "attachment; filename=\"project-export-test-data.csv\"")
                    .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                    .build();
        }
    }

    private TabularSchemaDTO resolveAppBasedExportSchema(PatientDataExportConfigDTO config) {
        ToolConfigsDTO appConfig = federatedAppConfigBO.findByAppVersionId(config.getGlobalAppVersionId());
        if (appConfig == null || appConfig.getOutput() == null) {
            return null;
        }

        return appConfig.getOutput().stream()
                .filter(Objects::nonNull)
                .filter(output -> output.getType() == ToolConfigDataType.CSV || output.getType() == ToolConfigDataType.TSV)
                .map(ToolOutputConfigDTO::getTabularSchema)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private TabularSchemaDTO createLongFormatSchema(PatientDataExportConfigDTO config) {
        LinkedHashMap<String, ColumnRuleDTO> columns = new LinkedHashMap<>();
        for (PatientDataPivotJoinField joinField : normalizeJoinFields(config)) {
            columns.put(columnName(joinField), joinColumnRule(joinField));
        }
        columns.put("feature_name", stringRule(false));
        columns.put("feature_value", stringRule(true));

        return createSchema(columns, true);
    }

    private TabularSchemaDTO createWideFormatSchema(
            PatientDataExportConfigDTO config,
            List<PatientExportFeatureDTO> features,
            Map<String, DataTypeNodeDTO> dataTypesById
    ) {
        LinkedHashMap<String, ColumnRuleDTO> columns = new LinkedHashMap<>();
        for (PatientDataPivotJoinField joinField : normalizeJoinFields(config)) {
            columns.put(columnName(joinField), joinColumnRule(joinField));
        }

        Set<String> featureNames = new LinkedHashSet<>();
        for (PatientExportFeatureDTO feature : features) {
            String featureName = resolveFeatureName(feature);
            if (!featureNames.add(featureName)) {
                throw new IllegalArgumentException("Duplicate export feature name: " + featureName);
            }

            DataTypeNodeDTO dataType = resolveDataType(feature, dataTypesById);
            columns.put(featureName, dataTypeRule(dataType));
        }

        return createSchema(columns, !features.isEmpty());
    }

    private TabularSchemaDTO createSchema(LinkedHashMap<String, ColumnRuleDTO> columns, boolean exactColumnCount) {
        TabularSchemaDTO schema = new TabularSchemaDTO();
        schema.setMinColumns(columns.size());
        if (exactColumnCount) {
            schema.setMaxColumns(columns.size());
        }
        schema.setRequiredColumns(new ArrayList<>(columns.keySet()));
        schema.setColumns(columns);
        return schema;
    }

    private Map<String, DataTypeNodeDTO> findDataTypes(List<PatientExportFeatureDTO> features) {
        List<UUID> dataTypeIds = features.stream()
                .map(this::resolveSelectedDataId)
                .filter(Objects::nonNull)
                .map(SelectedDataIdsDTO::getGlobalDataTypeId)
                .map(this::parseUuid)
                .flatMap(Optional::stream)
                .distinct()
                .toList();

        if (dataTypeIds.isEmpty()) {
            return Map.of();
        }

        return dataModelerDataTypeService.getByIds(dataTypeIds)
                .await().indefinitely()
                .stream()
                .filter(Objects::nonNull)
                .filter(dataType -> dataType.getId() != null)
                .collect(Collectors.toMap(
                        dataType -> dataType.getId().toString(),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private DataTypeNodeDTO resolveDataType(PatientExportFeatureDTO feature, Map<String, DataTypeNodeDTO> dataTypesById) {
        return Optional.ofNullable(resolveSelectedDataId(feature))
                .map(SelectedDataIdsDTO::getGlobalDataTypeId)
                .map(dataTypesById::get)
                .orElse(null);
    }

    private List<PatientExportFeatureDTO> getOrderedFeatures(PatientDataExportConfigDTO config) {
        if (config == null || config.getFeatures() == null || config.getFeatures().isEmpty()) {
            return List.of();
        }

        return config.getFeatures().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(feature -> Optional.ofNullable(feature.getOrder()).orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private SelectedDataIdsDTO resolveSelectedDataId(PatientExportFeatureDTO feature) {
        if (feature == null || feature.getAllowedDataIds() == null || feature.getAllowedDataIds().isEmpty()) {
            return null;
        }

        return feature.getAllowedDataIds().stream()
                .filter(Objects::nonNull)
                .filter(dataId -> dataId.getGlobalDataTypeId() != null)
                .filter(dataId -> feature.getTargetDatatypeId() != null
                        && feature.getTargetDatatypeId().contains(dataId.getGlobalDataTypeId()))
                .findFirst()
                .orElse(feature.getAllowedDataIds().stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    private String resolveFeatureName(PatientExportFeatureDTO feature) {
        if (feature.getName() != null && !feature.getName().isBlank()) {
            return feature.getName().trim();
        }
        if (feature.getTargetDatatypeId() != null && !feature.getTargetDatatypeId().isBlank()) {
            return feature.getTargetDatatypeId();
        }

        return Optional.ofNullable(resolveSelectedDataId(feature))
                .map(SelectedDataIdsDTO::getGlobalDataTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Project does not have selected data IDs defined"));
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private EnumSet<PatientDataPivotJoinField> normalizeJoinFields(PatientDataExportConfigDTO config) {
        if (config == null || config.getJoinFields() == null || config.getJoinFields().isEmpty()) {
            return EnumSet.copyOf(DEFAULT_JOIN_FIELDS);
        }
        return EnumSet.copyOf(config.getJoinFields());
    }

    private String columnName(PatientDataPivotJoinField joinField) {
        return switch (joinField) {
            case PATIENT_ID -> "patient_id";
            case VISIT_ID -> "visit_id";
            case VISIT_TIMESTAMP -> "visit_timestamp";
            case VISIT_TIMESTAMP_FORMAT -> "visit_timestamp_format";
            case IMPORT_SCHEMA_GROUP_ID -> "import_schema_group_id";
        };
    }

    private ColumnRuleDTO joinColumnRule(PatientDataPivotJoinField joinField) {
        return switch (joinField) {
            case PATIENT_ID -> integerRule(false);
            case VISIT_TIMESTAMP -> stringRule(true);
            case VISIT_ID, VISIT_TIMESTAMP_FORMAT, IMPORT_SCHEMA_GROUP_ID -> stringRule(true);
        };
    }

    private ColumnRuleDTO dataTypeRule(DataTypeNodeDTO dataType) {
        if (dataType == null) {
            return stringRule(true);
        }

        ColumnRuleDTO rule = new ColumnRuleDTO();
        rule.setType(mapDataType(dataType.getType()));
        rule.setNullable(Boolean.TRUE.equals(dataType.getAllowNullValues()) || !hasRequiredValidation(dataType));
        rule.setDescription(dataType.getDescription());
        if (dataType.getOptions() != null && !dataType.getOptions().isEmpty()) {
            rule.setEnumValues(dataType.getOptions());
        }

        for (DataTypeValidationDTO validation : Optional.ofNullable(dataType.getValidations()).orElse(List.of())) {
            applyValidation(rule, validation, dataType.getType());
        }
        return rule;
    }

    private ToolConfigHyperParamDataType mapDataType(DataTypes dataType) {
        if (dataType == null) {
            return ToolConfigHyperParamDataType.STRING;
        }

        return switch (dataType) {
            case INT -> ToolConfigHyperParamDataType.INTEGER;
            case FLOAT -> ToolConfigHyperParamDataType.FLOAT;
            case BOOLEAN -> ToolConfigHyperParamDataType.BOOLEAN;
            case CATEGORICAL -> ToolConfigHyperParamDataType.CATEGORICAL;
            case STRING, FILE, DATE, DATE_TIME -> ToolConfigHyperParamDataType.STRING;
        };
    }

    private boolean hasRequiredValidation(DataTypeNodeDTO dataType) {
        return dataType.isRequired();
    }

    private void applyValidation(ColumnRuleDTO rule, DataTypeValidationDTO validation, DataTypes dataType) {
        if (validation == null || validation.getName() == null || validation.getValidator() == null) {
            return;
        }

        switch (validation.getName()) {
            case PATTERN -> rule.setRegex(validation.getValidator());
            case MIN -> applyNumericBound(rule::setMin, validation.getValidator(), dataType);
            case MAX -> applyNumericBound(rule::setMax, validation.getValidator(), dataType);
            case MINLENGTH, MAXLENGTH -> {
                // TabularSchemaDTO has no string length fields; keep these on the datatype.
            }
        }
    }

    private void applyNumericBound(Consumer<Double> setter, String value, DataTypes dataType) {
        if (dataType != DataTypes.INT && dataType != DataTypes.FLOAT) {
            return;
        }
        try {
            setter.accept(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            // Keep schema creation tolerant of legacy validation values.
        }
    }

    private ColumnRuleDTO integerRule(boolean nullable) {
        ColumnRuleDTO rule = new ColumnRuleDTO();
        rule.setType(ToolConfigHyperParamDataType.INTEGER);
        rule.setNullable(nullable);
        return rule;
    }

    private ColumnRuleDTO stringRule(boolean nullable) {
        ColumnRuleDTO rule = new ColumnRuleDTO();
        rule.setType(ToolConfigHyperParamDataType.STRING);
        rule.setNullable(nullable);
        return rule;
    }
}
