package unit.importer;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingMode;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueMappingConfigDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueTargetDTO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.mapping.MappingRowResultDTO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class MappingBOTest {

    @Inject
    MappingBO mappingBO;

    @InjectMock
    ConnectorValidationBO validationBO;

    @InjectMock
    ConnectorRunPatientLogBO patientLogBO;

    @BeforeEach
    void setUp() {
        when(validationBO.validate(any(), anyLong())).thenAnswer(invocation ->
                new ConnectorValidationResultDTO(invocation.getArgument(1, Long.class)));
        when(validationBO.validate(any(), anyLong(), any(DataTypeNodeDTO.class))).thenAnswer(invocation ->
                new ConnectorValidationResultDTO(invocation.getArgument(1, Long.class)));
        doNothing().when(patientLogBO).createPatientLog(any(), anyLong(), any(), any());
    }

    @Test
    void applyMappingWithNoMappingSkipsColumn() {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setSchemaId(100L);
        mapping.setColumn("100");

        List<Map<String, Object>> rows = List.of(
                Map.of("999", "30")  // column 999 has no mapping
        );

        List<MappingRowResultDTO> results = mappingBO.applyMapping(1L, rows, List.of(mapping));

        assertEquals(1, results.size());
        assertTrue(results.get(0).getEntries().isEmpty());
    }

    @Test
    void applyMappingExtractsExternalId() {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn("Unique Patient ID");
        mapping.setMapping("Unique Patient ID");

        List<Map<String, Object>> rows = List.of(
                Map.of("Unique Patient ID", "PAT-001", "100", "42")
        );

        List<MappingRowResultDTO> results = mappingBO.applyMapping(1L, rows, List.of(mapping));

        assertEquals("PAT-001", results.get(0).getExternalPatientId());
    }

    @Test
    void applyMappingWithVisitMapping() {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setSchemaId(100L);
        mapping.setColumn("100");
        mapping.setVisitIdMapping("visit_col");
        mapping.setVisitTimestampMapping("timestamp_col");

        List<Map<String, Object>> rows = List.of(
                Map.of("100", "value", "visit_col", "V1", "timestamp_col", "2020-01-01")
        );

        List<MappingRowResultDTO> results = mappingBO.applyMapping(1L, rows, List.of(mapping));

        assertEquals(1, results.size());
        assertFalse(results.get(0).getEntries().isEmpty());
    }

    @Test
    void applyMappingRoutesValueColumnByKeyValue() {
        ConnectorMappingDTO mapping = advancedMapping(
                ConnectorMappingMode.VALUE_COLUMN,
                "code",
                "measurement",
                "heart_rate",
                100L
        );
        List<Map<String, Object>> rows = List.of(
                Map.of("code", "heart_rate", "measurement", 72)
        );

        List<MappingRowResultDTO> results = mappingBO.applyMapping(1L, rows, List.of(mapping));

        assertEquals(1, results.get(0).getEntries().size());
        assertEquals(100L, results.get(0).getEntries().get(0).getSchemaNodeId());
        assertEquals(72, results.get(0).getEntries().get(0).getValue());
    }

    @Test
    void applyMappingCreatesTrueEntryForOneHotValue() {
        ConnectorMappingDTO mapping = advancedMapping(
                ConnectorMappingMode.ONE_HOT,
                "diagnosis",
                "diagnosis",
                "V707",
                100L
        );
        List<Map<String, Object>> rows = List.of(
                Map.of("diagnosis", List.of("4139", "V707"))
        );

        List<MappingRowResultDTO> results = mappingBO.applyMapping(1L, rows, List.of(mapping));

        assertEquals(1, results.get(0).getEntries().size());
        assertEquals(Boolean.TRUE, results.get(0).getEntries().get(0).getValue());
    }

    @Test
    void getRowValueReturnsNullForMissingKey() {
        Map<String, Object> row = Map.of("a", "1");

        assertNull(mappingBO.getRowValue(row, "missing"));
        assertNull(mappingBO.getRowValue(row, null));
    }

    @Test
    void getRowValueReturnsStringForExistingKey() {
        Map<String, Object> row = Map.of("a", 42);

        assertEquals("42", mappingBO.getRowValue(row, "a"));
    }

    @Test
    void applyMappingEmptyRowsReturnsEmpty() {
        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setSchemaId(100L);

        List<MappingRowResultDTO> results = mappingBO.applyMapping(1L, List.of(), List.of(mapping));

        assertTrue(results.isEmpty());
    }

    private ConnectorMappingDTO advancedMapping(
            ConnectorMappingMode mode,
            String mappingColumn,
            String valueColumn,
            String sourceValue,
            Long schemaId) {
        ConnectorValueTargetDTO target = new ConnectorValueTargetDTO();
        target.setSourceValue(sourceValue);
        target.setValue("Demographics.Age");
        target.setSchemaId(schemaId);

        ConnectorValueMappingConfigDTO valueConfig = new ConnectorValueMappingConfigDTO();
        valueConfig.setMode(mode);
        valueConfig.setMappingColumn(mappingColumn);
        valueConfig.setValueColumn(valueColumn);
        valueConfig.setValueMappings(List.of(target));

        ConnectorMappingDTO mapping = new ConnectorMappingDTO();
        mapping.setColumn(mappingColumn);
        mapping.setValueMappingConfig(valueConfig);
        return mapping;
    }
}
