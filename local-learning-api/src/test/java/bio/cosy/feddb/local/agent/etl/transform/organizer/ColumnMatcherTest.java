package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.local.agent.etl.transform.config.ColumnMatcherConfig;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.text.SapbertSimilartity;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapperImpl;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeOptionsDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.helper.text.TextSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ColumnMatcherTest {

    private final ColumnMatcher matcher = new ColumnMatcher();
    private final List<LocalSchemaNodeWithPathDTO> nodes = new ArrayList<>();

    private final TableSample sample = new TableSample(
            List.of("age", "A1Cresult", "gender", "num_lab_procedures", "payer_code"),
            List.of(
                    Map.of("age", "45", "A1Cresult", ">7", "gender", "Male", "num_lab_procedures", "41", "payer_code", "?"),
                    Map.of("age", "71", "A1Cresult", "Norm", "gender", "Female", "num_lab_procedures", "3", "payer_code", "MC"),
                    Map.of("age", "62", "A1Cresult", ">8", "gender", "Female", "num_lab_procedures", "12", "payer_code", "?")
            ),
            null
    );

    @BeforeEach
    void setUp() {
        matcher.config = mock(ColumnMatcherConfig.class);
        when(matcher.config.candidates()).thenReturn(8);
        when(matcher.config.summaryValues()).thenReturn(8);
        when(matcher.config.textWeight()).thenReturn(0.6);
        when(matcher.config.semanticBaseline()).thenReturn(0.6);
        when(matcher.config.confidentText()).thenReturn(0.85);
        when(matcher.config.confidentMargin()).thenReturn(0.1);
        // Without semantic scores the matching relies on the names and values only
        matcher.sapbertSimilartity = mock(SapbertSimilartity.class);
        when(matcher.sapbertSimilartity.semanticScoresNodeNames(any(), any())).thenReturn(null);
        matcher.validationBO = new ConnectorValidationBO();
        matcher.dataTypeMapper = new DataTypeEntityMapperImpl();
    }

    @Test
    void mapsColumnsWithTheNameOfTheSchemaNodeDirectly() {
        node(1L, "Patient.age", "age", type(DataTypes.INT));
        node(2L, "Lab.A1C result", "A1C result", categorical(">7", ">8", "Norm", "None"));
        node(3L, "Patient.gender", "gender", categorical("Male", "Female"));

        Map<Long, List<ColumnCandidate>> candidates = match();

        assertDirect(candidates, 1L, "age");
        assertDirect(candidates, 2L, "A1Cresult");
        assertDirect(candidates, 3L, "gender");
    }

    @Test
    void ranksColumnsWithValidValuesFirstButLeavesThemToTheLlm() {
        node(1L, "Patient.sex", "sex", categorical("Male", "Female"));

        ColumnCandidate best = match().get(1L).getFirst();

        assertEquals("gender", best.getColumn());
        assertEquals(1.0, best.getValidRatio());
        assertFalse(best.isDirect());
    }

    @Test
    void assignsEachColumnDirectlyToOneSchemaNodeOnly() {
        node(1L, "Patient.age", "age", type(DataTypes.INT));
        node(2L, "Patient.age group", "age group", type(DataTypes.INT));

        Map<Long, List<ColumnCandidate>> candidates = match();

        assertDirect(candidates, 1L, "age");
        assertFalse(candidates.get(2L).getFirst().isDirect());
    }

    @Test
    void leavesColumnsWithMissingValuePlaceholdersToTheTransformerSelection() {
        node(1L, "Encounter.Payer Code", "Payer Code", type(DataTypes.STRING));

        ColumnCandidate best = match().get(1L).getFirst();

        assertEquals("payer_code", best.getColumn());
        assertEquals(0.5, best.getValidRatio());
        assertFalse(best.isDirect());
    }

    @Test
    void detectsMissingValuePlaceholdersUnlessTheSchemaAllowsThem() {
        assertTrue(ConnectorValidationBO.isMissingPlaceholder("?", null));
        assertTrue(ConnectorValidationBO.isMissingPlaceholder(" N/A ", List.of("Yes", "No")));
        assertFalse(ConnectorValidationBO.isMissingPlaceholder("?", List.of("?")));
        assertFalse(ConnectorValidationBO.isMissingPlaceholder("MC", null));
    }

    @Test
    void comparesNamesIgnoringCaseAndSeparators() {
        assertEquals(1.0, TextSimilarity.nameSimilarity("A1Cresult", "A1C result"));
        assertEquals(1.0, TextSimilarity.nameSimilarity("num_lab_procedures", "Num Lab Procedures"));
        assertTrue(TextSimilarity.nameSimilarity("num_lab_procedures", "number of lab procedures") < 1.0);
    }

    private Map<Long, List<ColumnCandidate>> match() {
        return matcher.match(sample, nodes, Map.of());
    }

    private void assertDirect(Map<Long, List<ColumnCandidate>> candidates, Long schemaId, String column) {
        ColumnCandidate best = candidates.get(schemaId).getFirst();
        assertEquals(column, best.getColumn());
        assertTrue(best.isDirect(), column + " should be mapped directly");
    }

    private void node(Long id, String path, String name, DataTypeDTO dataType) {
        LocalSchemaNodeWithPathDTO node = new LocalSchemaNodeWithPathDTO();
        node.setId(id);
        node.setName(name);
        node.setPath(path);
        node.setDataType(dataType);
        nodes.add(node);
    }

    private static DataTypeDTO type(DataTypes type) {
        DataTypeDTO dataType = new DataTypeDTO();
        dataType.setName(type.name());
        dataType.setType(type);
        return dataType;
    }

    private static DataTypeDTO categorical(String... options) {
        DataTypeDTO dataType = type(DataTypes.CATEGORICAL);
        dataType.setOptions(Arrays.stream(options).map(DataTypeOptionsDTO::new)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return dataType;
    }
}
