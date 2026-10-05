package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.organizer.ColumnMatcher;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.api.importer.files.read.TableDataProfileBO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ColumnMatcherTest {

    private final ColumnMatcher matcher = new ColumnMatcher();
    private final Map<String, LocalSchemaNodeNestedDTO> nodes = new LinkedHashMap<>();
    private final Map<Long, DataTypeNodeDTO> dataTypes = new LinkedHashMap<>();

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
    void setUp() throws Exception {
        matcher.validationBO = new ConnectorValidationBO();
        matcher.profileBO = new TableDataProfileBO(new FileAnalytics());
        matcher.previewValidationBO = previewValidation();
    }

    public static PreviewValidationBO previewValidation() throws Exception {
        PreviewValidationBO previewValidation = new PreviewValidationBO();
        Field maxCategoricalValues = PreviewValidationBO.class.getDeclaredField("maxCategoricalValues");
        maxCategoricalValues.setAccessible(true);
        maxCategoricalValues.setInt(previewValidation, 50);
        return previewValidation;
    }

    @Test
    void mapsColumnsWithTheNameOfTheSchemaNodeDirectly() {
        node(1L, "Patient.age", "age", type(DataTypes.INT));
        node(2L, "Lab.A1C result", "A1C result", categorical(">7", ">8", "Norm", "None"));
        node(3L, "Patient.gender", "gender", categorical("Male", "Female"));

        Map<Long, List<ColumnCandidate>> candidates = match(Map.of());

        assertDirect(candidates, 1L, "age");
        assertDirect(candidates, 2L, "A1Cresult");
        assertDirect(candidates, 3L, "gender");
    }

    @Test
    void ranksColumnsWithValidValuesFirstButLeavesThemToTheLlm() {
        node(1L, "Patient.sex", "sex", categorical("Male", "Female"));

        ColumnCandidate best = match(Map.of()).get(1L).getFirst();

        assertEquals("gender", best.getColumn());
        assertEquals(1.0, best.getValidRatio());
        assertFalse(best.isDirect());
    }

    @Test
    void mapsColumnsAcceptedInOtherConnectorsDirectly() {
        node(1L, "Patient.sex", "sex", categorical("Male", "Female"));

        ColumnCandidate best = match(Map.of("Patient.sex", Set.of("gender"))).get(1L).getFirst();

        assertEquals("gender", best.getColumn());
        assertTrue(best.isDirect());
    }

    @Test
    void assignsEachColumnDirectlyToOneSchemaNodeOnly() {
        node(1L, "Patient.age", "age", type(DataTypes.INT));
        node(2L, "Patient.age group", "age group", type(DataTypes.INT));

        Map<Long, List<ColumnCandidate>> candidates = match(Map.of());

        assertDirect(candidates, 1L, "age");
        assertFalse(candidates.get(2L).getFirst().isDirect());
    }

    @Test
    void leavesColumnsWithMissingValuePlaceholdersToTheTransformerSelection() {
        node(1L, "Encounter.Payer Code", "Payer Code", type(DataTypes.STRING));

        ColumnCandidate best = match(Map.of()).get(1L).getFirst();

        assertEquals("payer_code", best.getColumn());
        assertEquals(0.5, best.getValidRatio());
        assertFalse(best.isDirect());
    }

    @Test
    void detectsMissingValuePlaceholdersUnlessTheSchemaAllowsThem() {
        assertTrue(TransformerValidateTool.isMissingPlaceholder("?", null));
        assertTrue(TransformerValidateTool.isMissingPlaceholder(" N/A ", List.of("Yes", "No")));
        assertFalse(TransformerValidateTool.isMissingPlaceholder("?", List.of("?")));
        assertFalse(TransformerValidateTool.isMissingPlaceholder("MC", null));
    }

    @Test
    void comparesNamesIgnoringCaseAndSeparators() {
        assertEquals(1.0, ColumnMatcher.nameSimilarity("A1Cresult", "A1C result"));
        assertEquals(1.0, ColumnMatcher.nameSimilarity("num_lab_procedures", "Num Lab Procedures"));
        assertTrue(ColumnMatcher.nameSimilarity("num_lab_procedures", "number of lab procedures") < 1.0);
    }

    private Map<Long, List<ColumnCandidate>> match(Map<String, Set<String>> acceptedColumns) {
        Map<String, ColumnProfile> profiles = new HashMap<>();
        matcher.profileBO.profiles(sample.toTableData()).forEach(profile -> profiles.put(profile.name(), profile));
        Map<String, List<Object>> values = new HashMap<>();
        profiles.forEach((column, profile) -> values.put(column, List.copyOf(matcher.previewValidationBO.values(profile))));
        return matcher.match(sample.getColumns(), nodes, values, profiles, dataTypes, acceptedColumns);
    }

    private void assertDirect(Map<Long, List<ColumnCandidate>> candidates, Long schemaId, String column) {
        ColumnCandidate best = candidates.get(schemaId).getFirst();
        assertEquals(column, best.getColumn());
        assertTrue(best.isDirect(), column + " should be mapped directly");
    }

    private void node(Long id, String path, String name, DataTypeNodeDTO dataType) {
        LocalSchemaNodeNestedDTO node = new LocalSchemaNodeNestedDTO();
        node.setId(id);
        node.setName(name);
        nodes.put(path, node);
        dataTypes.put(id, dataType);
    }

    private static DataTypeNodeDTO type(DataTypes type) {
        DataTypeNodeDTO dataType = new DataTypeNodeDTO();
        dataType.setName(type.name());
        dataType.setType(type);
        return dataType;
    }

    private static DataTypeNodeDTO categorical(String... options) {
        DataTypeNodeDTO dataType = type(DataTypes.CATEGORICAL);
        dataType.setOptions(List.of(options));
        return dataType;
    }
}
