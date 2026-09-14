package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.local.api.cohort.CohortDetailDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingMode;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueMappingConfigDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueTargetDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ConnectorSchemaHelper {

    @Inject
    FLNetClientConfig config;

    public ConnectorDTO validateAndRematchMappingSchemaId(ConnectorDTO connector, CohortDetailDTO cohort) {
        if (connector == null || !requiresSchemaValidation(connector)) {
            return connector;
        }

        validateCohortSchema(cohort);

        if (hasMergeConfig(connector)) {
            validateMergeConfig(connector.getMergeConfig());
        }

        if (hasSchemaMapping(connector)) {
            validateSchemaMappings(connector.getSchemaMapping(), cohort);
            rematchMappingSchemaId(connector, cohort);
            verifyRematchedSchemaIds(connector.getSchemaMapping());
        }

        return connector;
    }

    public ConnectorDTO rematchMappingSchemaId(ConnectorDTO config, CohortDetailDTO cohort) {
        if (config == null || config.getSchemaMapping() == null || config.getSchemaMapping().isEmpty()) {
            return config;
        }
        if (cohort == null || cohort.getSchemaRoot() == null || cohort.getSchemaRoot().getChildNodes() == null) {
            return config;
        }

        for (ConnectorMappingDTO schemaMapping : config.getSchemaMapping()) {
            if (schemaMapping == null) {
                continue;
            }
            if (schemaMapping.getMapping() != null) {
                schemaMapping.setSchemaId(findSchemaId(schemaMapping.getMapping(), cohort.getSchemaRoot().getChildNodes()));
            }
            ConnectorValueMappingConfigDTO valueConfig = schemaMapping.getValueMappingConfig();
            if (valueConfig != null && valueConfig.getValueMappings() != null) {
                for (ConnectorValueTargetDTO target : valueConfig.getValueMappings()) {
                    if (target != null && target.getValue() != null) {
                        target.setSchemaId(findSchemaId(target.getValue(), cohort.getSchemaRoot().getChildNodes()));
                    }
                }
            }
        }

        return config;
    }

    public Long findSchemaId(String mapping, Set<LocalSchemaNodeNestedDTO> nodes) {
        if (mapping == null || mapping.isBlank() || nodes == null || nodes.isEmpty()) {
            return null;
        }

        if (mapping.contains(".")) {
            String left = mapping.split("\\.", 2)[0];
            String others = mapping.substring(left.length() + 1);

            for (LocalSchemaNodeNestedDTO node : nodes) {
                if (node != null
                        && left.equals(node.getName())
                        && node.getType() == SchemaNodeType.GROUP
                        && node.getChildNodes() != null
                        && !node.getChildNodes().isEmpty()) {
                    return findSchemaId(others, node.getChildNodes());
                }
            }
        }

        for (LocalSchemaNodeNestedDTO node : nodes) {
            if (node != null
                    && mapping.equals(node.getName())
                    && (node.getType() == SchemaNodeType.ATOMIC_ATTRIBUTE || node.getType() == SchemaNodeType.LIST_ATTRIBUTE)) {
                return node.getId();
            }
        }

        return null;
    }

    private boolean requiresSchemaValidation(ConnectorDTO connector) {
        return hasSchemaMapping(connector) || hasMergeConfig(connector);
    }

    private boolean hasSchemaMapping(ConnectorDTO connector) {
        return connector.getSchemaMapping() != null && !connector.getSchemaMapping().isEmpty();
    }

    private boolean hasMergeConfig(ConnectorDTO connector) {
        SheetMergeResultDTO mergeConfig = connector.getMergeConfig();
        return mergeConfig != null
                && mergeConfig.getSheetUidMapping() != null
                && !mergeConfig.getSheetUidMapping().isEmpty();
    }

    private void validateCohortSchema(CohortDetailDTO cohort) {
        if (cohort == null) {
            throw new BadRequestException("Cohort is required for connector schema validation");
        }
        if (cohort.getSchemaRoot() == null || cohort.getSchemaRoot().getChildNodes() == null || cohort.getSchemaRoot().getChildNodes().isEmpty()) {
            throw new BadRequestException("Cohort schema is not available for connector validation");
        }
    }

    private void validateMergeConfig(SheetMergeResultDTO mergeConfig) {
        Map<String, String> sheetUidMapping = mergeConfig.getSheetUidMapping();
        if (sheetUidMapping == null || sheetUidMapping.isEmpty()) {
            throw new BadRequestException("mergeConfig requires at least one sheet UID mapping");
        }

        List<String> invalidEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : sheetUidMapping.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                invalidEntries.add("(blank sheet name)");
                continue;
            }
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                invalidEntries.add(entry.getKey() + " (missing UID column)");
            }
        }

        if (!invalidEntries.isEmpty()) {
            throw new BadRequestException("Invalid mergeConfig sheet UID mappings: " + String.join(", ", invalidEntries));
        }
    }

    private void validateSchemaMappings(List<ConnectorMappingDTO> schemaMappings, CohortDetailDTO cohort) {
        String externalIdColumn = config.connector().externalIdColumn();
        Set<LocalSchemaNodeNestedDTO> schemaNodes = cohort.getSchemaRoot().getChildNodes();

        List<String> missingColumns = new ArrayList<>();
        List<String> unknownMappings = new ArrayList<>();
        List<String> invalidValueMappings = new ArrayList<>();

        for (ConnectorMappingDTO mapping : schemaMappings) {
            if (mapping == null) {
                continue;
            }

            ConnectorValueMappingConfigDTO valueConfig = mapping.getValueMappingConfig();
            if (valueConfig != null) {
                validateValueMapping(valueConfig, schemaNodes, missingColumns, unknownMappings, invalidValueMappings);
                continue;
            }

            if (StringUtils.isBlank(mapping.getColumn())) {
                missingColumns.add(StringUtils.defaultIfBlank(mapping.getMapping(), "(unknown mapping)"));
                continue;
            }

            String mappingPath = mapping.getMapping();
            if (StringUtils.isBlank(mappingPath) || externalIdColumn.equals(mappingPath)) {
                continue;
            }

            if (findSchemaId(mappingPath, schemaNodes) == null) {
                unknownMappings.add(mappingPath);
            }
        }

        if (!missingColumns.isEmpty()) {
            throw new BadRequestException("Schema mapping is missing file columns for: " + String.join(", ", missingColumns));
        }
        if (!unknownMappings.isEmpty()) {
            throw new BadRequestException("Schema mapping fields not found in cohort schema: " + String.join(", ", unknownMappings));
        }
        if (!invalidValueMappings.isEmpty()) {
            throw new BadRequestException("Invalid value mappings: " + String.join(", ", invalidValueMappings));
        }
    }

    private void verifyRematchedSchemaIds(List<ConnectorMappingDTO> schemaMappings) {
        String externalIdColumn = config.connector().externalIdColumn();
        List<String> unresolvedMappings = new ArrayList<>();

        for (ConnectorMappingDTO mapping : schemaMappings) {
            if (mapping == null) {
                continue;
            }

            ConnectorValueMappingConfigDTO valueConfig = mapping.getValueMappingConfig();
            if (valueConfig != null && valueConfig.getValueMappings() != null) {
                for (ConnectorValueTargetDTO target : valueConfig.getValueMappings()) {
                    if (target != null && StringUtils.isNotBlank(target.getValue()) && target.getSchemaId() == null) {
                        unresolvedMappings.add(target.getValue());
                    }
                }
                continue;
            }

            String mappingPath = mapping.getMapping();
            if (StringUtils.isBlank(mappingPath) || externalIdColumn.equals(mappingPath)) {
                continue;
            }

            if (mapping.getSchemaId() == null) {
                unresolvedMappings.add(mappingPath);
            }
        }

        if (!unresolvedMappings.isEmpty()) {
            throw new BadRequestException("Could not resolve schema mapping fields for cohort schema: " + String.join(", ", unresolvedMappings));
        }
    }

    private void validateValueMapping(
            ConnectorValueMappingConfigDTO valueConfig,
            Set<LocalSchemaNodeNestedDTO> schemaNodes,
            List<String> missingColumns,
            List<String> unknownMappings,
            List<String> invalidValueMappings) {
        if (valueConfig.getMode() == null || valueConfig.getMode() == ConnectorMappingMode.DIRECT) {
            invalidValueMappings.add("mapping mode must be VALUE_COLUMN or ONE_HOT");
        }
        if (StringUtils.isBlank(valueConfig.getMappingColumn())) {
            missingColumns.add("value mapping key column");
        }
        if (StringUtils.isBlank(valueConfig.getValueColumn())) {
            missingColumns.add("value mapping source column");
        }
        if (valueConfig.getValueMappings() == null || valueConfig.getValueMappings().isEmpty()) {
            invalidValueMappings.add("at least one source value must be mapped");
            return;
        }

        for (ConnectorValueTargetDTO target : valueConfig.getValueMappings()) {
            if (target == null || StringUtils.isBlank(target.getValue())) {
                invalidValueMappings.add("schema target is required");
                continue;
            }
            if (findSchemaId(target.getValue(), schemaNodes) == null) {
                unknownMappings.add(target.getValue());
            }
        }
    }
}
