package unit.importer;

import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBulkRequestDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorValidationBOTest {

    private final ConnectorValidationBO validationBO = new ConnectorValidationBO() {
        @Override
        public ConnectorValidationResultDTO validate(Object value, Long schemaId) {
            return new ConnectorValidationResultDTO(String.valueOf(value), schemaId, true);
        }

        @Override
        protected boolean isExternalIdMapping(String mapping) {
            return "Unique Patient ID".equals(mapping);
        }
    };

    @Test
    void externalIdMappingIsAlwaysValidWithoutSchemaId() {
        ConnectorValidationResultDTO result = validationBO.checkValidations(
                1L,
                "",
                null,
                "Unique Patient ID",
                "user"
        );

        assertEquals(true, result.isValid());
        assertEquals("Valid", result.getMessage());
    }

    @Test
    void bulkValidationSupportsSingleAndMultipleValuesInRequestOrder() {
        ConnectorValidationBulkRequestDTO single = new ConnectorValidationBulkRequestDTO();
        single.setSchemaId(10L);
        single.setValue("category-a");

        ConnectorValidationBulkRequestDTO multiple = new ConnectorValidationBulkRequestDTO();
        multiple.setSchemaId(20L);
        multiple.setValue(0);
        multiple.setValues(List.of(1, 25, 100));

        ConnectorValidationBulkRequestDTO externalId = new ConnectorValidationBulkRequestDTO();
        externalId.setMapping("Unique Patient ID");
        externalId.setValues(List.of("PAT-1", ""));

        List<ConnectorValidationResultDTO> results = validationBO.checkValidationsBulk(
                1L,
                List.of(single, multiple, externalId),
                "user"
        );

        assertEquals(List.of("category-a", "0", "1", "25", "100", "Valid", "Valid"),
                results.stream().map(ConnectorValidationResultDTO::getMessage).toList());
        assertEquals(7, results.size());
        assertEquals(List.of(10L, 20L, 20L, 20L, 20L),
                results.stream()
                        .map(ConnectorValidationResultDTO::getSchemaId)
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    @Test
    void blankValueUsesTargetNullabilityEvenWithoutOtherValidationRules() {
        DataTypeNodeDTO nullable = dataType(false, true);
        DataTypeNodeDTO required = dataType(true, true);
        DataTypeNodeDTO nonNullable = dataType(false, false);

        assertTrue(validationBO.validate("", 10L, nullable).isValid());
        assertFalse(validationBO.validate("", 10L, required).isValid());
        assertFalse(validationBO.validate("", 10L, nonNullable).isValid());
    }

    private DataTypeNodeDTO dataType(boolean required, boolean allowNullValues) {
        DataTypeNodeDTO result = new DataTypeNodeDTO();
        result.setName("Test field");
        result.setType(DataTypes.STRING);
        result.setIsRequired(required);
        result.setAllowNullValues(allowNullValues);
        return result;
    }
}
