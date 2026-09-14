package bio.cosy.feddb.local.api.importer.mapping;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeHelper;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MappingBO {

    @Inject
    ConnectorValidationBO validationBO;

    @Inject
    FLNetClientConfig config;

    @Inject
    ConnectorRunPatientLogBO patientLogBO;

    @Inject
    SchemaNodeAO schemaNodeAO;

    public String resolveExternalIdSourceColumn(List<ConnectorMappingDTO> schemaMapping) {
        if (schemaMapping == null) {
            return null;
        }
        String externalIdColumn = config.connector().externalIdColumn();
        return schemaMapping.stream()
                .filter(mapping -> mapping != null
                        && externalIdColumn.equals(mapping.getMapping())
                        && mapping.getColumn() != null
                        && !mapping.getColumn().isBlank())
                .map(ConnectorMappingDTO::getColumn)
                .findFirst()
                .orElse(null);
    }

    public List<MappingRowResultDTO> applyMapping(
            Long runId,
            List<Map<String, Object>> rows,
            List<ConnectorMappingDTO> schemaMapping) {
        return applyMapping(runId, rows, buildContext(schemaMapping));
    }

    // Builds the per-run lookup context once (incl. the validation-node DB query) so the streaming
    // drain maps a patient group at a time without rebuilding it (and re-querying) per patient.
    public MappingContext buildContext(List<ConnectorMappingDTO> schemaMapping) {
        Map<String, ConnectorMappingDTO> mappingByColumn = new HashMap<>();
        Map<Long, ConnectorMappingDTO> mappingBySchemaId = new HashMap<>();
        List<ConnectorMappingDTO> advancedMappings = new ArrayList<>();
        Map<Long, DataTypeNodeDTO> validationNodeBySchemaId = loadValidationNodes(schemaMapping);

        if (schemaMapping != null) {
            for (ConnectorMappingDTO mapping : schemaMapping) {
                if (mapping == null) {
                    continue;
                }
                if (mapping.getValueMappingConfig() != null) {
                    advancedMappings.add(mapping);
                    for (ConnectorValueTargetDTO target : valueTargets(mapping)) {
                        if (target != null && target.getSchemaId() != null) {
                            mappingBySchemaId.put(target.getSchemaId(), targetMapping(mapping, target));
                        }
                    }
                    continue;
                }
                if (mapping.getColumn() != null) {
                    mappingByColumn.put(mapping.getColumn(), mapping);
                }
                if (mapping.getSchemaId() != null) {
                    mappingBySchemaId.put(mapping.getSchemaId(), mapping);
                }
            }
        }
        return new MappingContext(config.connector().externalIdColumn(),
                mappingByColumn, mappingBySchemaId, validationNodeBySchemaId, advancedMappings);
    }

    public List<MappingRowResultDTO> applyMapping(
            Long runId,
            List<Map<String, Object>> rows,
            MappingContext context) {

        String externalIdColumn = context.externalIdColumn();
        Map<String, ConnectorMappingDTO> mappingByColumn = context.mappingByColumn();
        Map<Long, ConnectorMappingDTO> mappingBySchemaId = context.mappingBySchemaId();
        Map<Long, DataTypeNodeDTO> validationNodeBySchemaId = context.validationNodeBySchemaId();

        List<MappingRowResultDTO> results = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            MappingRowResultDTO result = new MappingRowResultDTO();
            List<ConnectorValidationResultDTO> validationResults = new ArrayList<>();
            for (Map.Entry<String, Object> cell : row.entrySet()) {
                // Skip cells that are literally null. These are structural placeholders
                // produced by the tabular reader's sheet merge: when rows from different sheets
                // are merged side-by-side, columns owned by another sheet are pre-filled
                // with null for the current row's source sheet. They do not represent a
                // real "missing value" for that row, so we must not validate or persist
                // them. Real missing values from the source (e.g. empty CSV cells) arrive
                // here as the empty string "" and continue through validation/load below.
                if (cell.getValue() == null) {
                    continue;
                }
                ConnectorMappingDTO mapping = mappingByColumn.get(cell.getKey());
                if (mapping == null) continue;
                if (externalIdColumn.equals(mapping.getMapping())) {
                    result.setExternalPatientId(cell.getValue().toString());
                    continue;
                }
                DataTypeNodeDTO node = validationNodeBySchemaId.get(mapping.getSchemaId());

                if (node != null && isEmptyValue(cell.getValue()) && !node.allowNullValues() && !node.isRequired()) {
                    Log.debugf("Skipping empty value for column %s node %s as allowNullValues is false and node is not required", cell.getKey(), node.getName());
                    continue;
                }

                boolean isNullValue = isEmptyValue(cell.getValue()) && node != null && node.allowNullValues();
                result.addEntry(mapping, cell.getValue(),
                        getRowValue(row, mapping.getVisitIdMapping()),
                        getRowValue(row, mapping.getVisitTimestampMapping()),
                        isNullValue);
                ConnectorValidationResultDTO validationResult = validationBO.validate(
                        cell.getValue(),
                        mapping.getSchemaId(),
                        node
                );
                validationResults.add(validationResult);

            }
            for (ConnectorMappingDTO mapping : context.advancedMappings()) {
                applyAdvancedMapping(row, mapping, result, validationResults, validationNodeBySchemaId);
            }
            result.setValidationResult(validationResults);
            if (StringUtils.isEmpty(result.getExternalPatientId())) {
                createPatientLog("Missing external patient ID for row with data: " + row, runId, "Unknown", null);
            } else {
                for (ConnectorValidationResultDTO validationResult : validationResults) {
                    if (!validationResult.isValid()) {
                        ConnectorMappingDTO mapping = mappingBySchemaId.get(validationResult.getSchemaId());
                        String cell = mapping != null
                                ? mapping.getColumn()
                                : "Unknown column";
                        createPatientLog(validationResult.getMessage(), runId, result.getExternalPatientId(), cell);
                    }
                }
            }
            results.add(result);
        }

        return results;

    }

    private Map<Long, DataTypeNodeDTO> loadValidationNodes(List<ConnectorMappingDTO> schemaMapping) {
        Map<Long, DataTypeNodeDTO> validationNodeBySchemaId = new HashMap<>();
        List<Long> schemaIds = schemaMapping == null
                ? List.of()
                : schemaMapping.stream()
                  .filter(mapping -> mapping != null)
                  .flatMap(mapping -> {
                      if (mapping.getValueMappingConfig() == null) {
                          return java.util.stream.Stream.of(mapping.getSchemaId());
                      }
                      return valueTargets(mapping).stream()
                              .filter(target -> target != null)
                              .map(ConnectorValueTargetDTO::getSchemaId);
                  })
                  .filter(id -> id != null)
                  .distinct()
                  .toList();

        if (schemaIds.isEmpty()) {
            return validationNodeBySchemaId;
        }

        for (SchemaNodeEntity schemaNode : schemaNodeAO.find("id in ?1", schemaIds).list()) {
            if (schemaNode == null || schemaNode.getId() == null || schemaNode.getDataType() == null) {
                continue;
            }
            validationNodeBySchemaId.put(schemaNode.getId(), DataTypeHelper.toDataTypeNodeDTO(schemaNode.getDataType()));
        }

        return validationNodeBySchemaId;
    }

    private boolean isEmptyValue(Object value) {
        return value == null || value.toString().isEmpty();
    }

    private void applyAdvancedMapping(
            Map<String, Object> row,
            ConnectorMappingDTO mapping,
            MappingRowResultDTO result,
            List<ConnectorValidationResultDTO> validationResults,
            Map<Long, DataTypeNodeDTO> validationNodeBySchemaId) {
        ConnectorValueMappingConfigDTO valueConfig = mapping.getValueMappingConfig();
        if (valueConfig == null || valueConfig.getMode() == null) {
            return;
        }

        List<String> rowKeys = valuesOf(row.get(valueConfig.getMappingColumn())).stream()
                .map(this::normalizeRoutingValue)
                .toList();
        if (rowKeys.isEmpty()) {
            return;
        }

        for (ConnectorValueTargetDTO target : valueTargets(mapping)) {
            if (target == null || !rowKeys.contains(normalizeRoutingValue(target.getSourceValue()))) {
                continue;
            }
            Object value = valueConfig.getMode() == ConnectorMappingMode.ONE_HOT
                    ? true
                    : row.get(valueConfig.getValueColumn());
            if (value == null) {
                continue;
            }
            addAdvancedEntry(row, mapping, target, value, result, validationResults, validationNodeBySchemaId);
        }
    }

    private void addAdvancedEntry(
            Map<String, Object> row,
            ConnectorMappingDTO parentMapping,
            ConnectorValueTargetDTO target,
            Object value,
            MappingRowResultDTO result,
            List<ConnectorValidationResultDTO> validationResults,
            Map<Long, DataTypeNodeDTO> validationNodeBySchemaId) {
        ConnectorMappingDTO mapping = targetMapping(parentMapping, target);
        DataTypeNodeDTO node = validationNodeBySchemaId.get(target.getSchemaId());
        if (node != null && isEmptyValue(value) && !node.allowNullValues() && !node.isRequired()) {
            return;
        }

        boolean isNullValue = isEmptyValue(value) && node != null && node.allowNullValues();
        result.addEntry(mapping, value,
                getRowValue(row, parentMapping.getVisitIdMapping()),
                getRowValue(row, parentMapping.getVisitTimestampMapping()),
                isNullValue);
        validationResults.add(validationBO.validate(value, target.getSchemaId(), node));
    }

    private ConnectorMappingDTO targetMapping(ConnectorMappingDTO parent, ConnectorValueTargetDTO target) {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn(parent.getValueMappingConfig().getValueColumn());
        mapping.setMapping(target.getValue());
        mapping.setSchemaId(target.getSchemaId());
        mapping.setVisitIdMapping(parent.getVisitIdMapping());
        mapping.setVisitTimestampMapping(parent.getVisitTimestampMapping());
        mapping.setTimestampFormat(parent.getTimestampFormat());
        return mapping;
    }

    private List<ConnectorValueTargetDTO> valueTargets(ConnectorMappingDTO mapping) {
        ConnectorValueMappingConfigDTO config = mapping.getValueMappingConfig();
        return config == null || config.getValueMappings() == null ? List.of() : config.getValueMappings();
    }

    private List<Object> valuesOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                values.add(Array.get(value, index));
            }
            return values;
        }
        return List.of(value);
    }

    private String normalizeRoutingValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public String getRowValue(Map<String, Object> row, String key) {
        if (key == null) {
            return null;
        }
        Object result = row.getOrDefault(key, null);
        return result != null ? result.toString() : null;
    }

    private void createPatientLog(String message, Long runId, String externalPatientId, String cell) {
        if (patientLogBO == null) {
            return;
        }
        patientLogBO.createPatientLog(message, runId, externalPatientId, cell);
    }

    // Reusable lookup tables built once per run and shared across all patient groups.
    public record MappingContext(String externalIdColumn,
                                 Map<String, ConnectorMappingDTO> mappingByColumn,
                                 Map<Long, ConnectorMappingDTO> mappingBySchemaId,
                                 Map<Long, DataTypeNodeDTO> validationNodeBySchemaId,
                                 List<ConnectorMappingDTO> advancedMappings) {
    }
}
