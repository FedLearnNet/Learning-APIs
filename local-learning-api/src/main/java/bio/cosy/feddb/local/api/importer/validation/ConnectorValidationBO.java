package bio.cosy.feddb.local.api.importer.validation;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.validation.BaseValidationBO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeHelper;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConnectorValidationBO extends BaseValidationBO {

    @Inject
    SchemaNodeAO schemaNodeAO;


    @Inject
    FLNetClientConfig config;

    public ConnectorValidationResultDTO checkValidations(
            Long cohortId,
            String value,
            Long schemaId,
            String mapping,
            String keycloakId) {
        if (cohortId == null) {
            return new ConnectorValidationResultDTO("Cohort Id is required");
        }
        if (isExternalIdMapping(mapping)) {
            return new ConnectorValidationResultDTO(schemaId);
        }
        return validate(value, schemaId);
    }

    public List<ConnectorValidationResultDTO> checkValidationsBulk(Long cohortId, List<ConnectorValidationBulkRequestDTO> items, String keycloakId) {
        if (cohortId == null) {
            return List.of(new ConnectorValidationResultDTO("Cohort Id is required"));
        }
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .flatMap(entry -> {
                    if (entry == null) {
                        return java.util.stream.Stream.of(
                                new ConnectorValidationResultDTO("Validation item is required")
                        );
                    }
                    return entry.resolvedValues().stream()
                            .map(value -> isExternalIdMapping(entry.getMapping())
                                    ? new ConnectorValidationResultDTO(entry.getSchemaId())
                                    : validate(value, entry.getSchemaId()));
                })
                .toList();
    }

    protected boolean isExternalIdMapping(String mapping) {
        return mapping != null && mapping.equals(config.connector().externalIdColumn());
    }


    public ConnectorValidationResultDTO validate(Object value, Long schemaId) {
        if (schemaId == null) {
            return new ConnectorValidationResultDTO("SchemaId is required");
        }
        Optional<SchemaNodeEntity> nodeOpt = schemaNodeAO.findByIdOptional(schemaId);
        if (nodeOpt.isEmpty()) {
            return new ConnectorValidationResultDTO("Schema not found");
        }
        if (nodeOpt.get().getDataType() == null) {
            return new ConnectorValidationResultDTO("DataType not found");
        }
        DataTypeNodeDTO node = DataTypeHelper.toDataTypeNodeDTO(nodeOpt.get().getDataType());
        return validate(value, schemaId, node);
    }

    public ConnectorValidationResultDTO validate(Object value, Long schemaId, DataTypeNodeDTO node) {
        if (schemaId == null) {
            return new ConnectorValidationResultDTO("SchemaId  is required");
        }
        if (node == null) {
            return new ConnectorValidationResultDTO("Schema not found");
        }
        // Empty values (null or "") need special handling:
        //  - if the node is required, the value must be present regardless of allowNullValues
        //  - else if allowNullValues is set, the empty value is accepted and the entry will
        //    be persisted with isNullValue=true (no type/format validation possible)
        //  - else the value is rejected as required
        if (valueIsEmpty(value)) {
            if (isRequired(node)) {
                return new ConnectorValidationResultDTO("Value is required", schemaId);
            }
            if (node.allowNullValues()) {
                return new ConnectorValidationResultDTO(schemaId);
            }
            return new ConnectorValidationResultDTO("Value is required", schemaId);
        }
        try {
            Object normalizedValue = normalizeValue(value, node);
            validateValue(normalizedValue, node);
            return new ConnectorValidationResultDTO(schemaId);
        } catch (Exception e) {
            return new ConnectorValidationResultDTO(e.getMessage(), schemaId);
        }
    }
}
