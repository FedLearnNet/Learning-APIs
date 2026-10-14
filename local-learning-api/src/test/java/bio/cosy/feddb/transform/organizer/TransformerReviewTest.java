package bio.cosy.feddb.transform.organizer;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.organizer.TransformerReview;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransformerReviewTest {

    @Mock
    ConnectorTransformationBO transformationBO;

    @InjectMocks
    TransformerReview review;

    private final List<LocalSchemaNodeWithPathDTO> nodes = new ArrayList<>();
    private final Map<Long, ConnectorSelectedTransformer> selected = new HashMap<>();
    private final Map<String, ColumnProfile> profiles = new HashMap<>();
    private final List<String> columns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // A value mapper: every transformer maps the values of its column with the map in its hyperparams
        when(transformationBO.applyBuiltInTransformations(anyList(), anyList(), any())).thenAnswer(invocation -> {
            List<ConnectorTransformerDTO> transformers = invocation.getArgument(0);
            List<Map<String, Object>> rows = invocation.getArgument(1);
            for (ConnectorTransformerDTO transformer : transformers) {
                Map<?, ?> map = (Map<?, ?>) transformer.getHyperparams().get("map");
                rows.forEach(row -> row.computeIfPresent(transformer.getColumn(), (column, value) -> map.containsKey(value) ? map.get(value) : value));
            }
            return rows;
        });
    }

    @Test
    void listsTheColumnsNoSchemaNodeUses() {
        drug(1L, "rx_a", Map.of("-", "No"), "-");
        drug(2L, "rx_b", Map.of("-", "No"), "-");
        profiles.put("encounter_codes", profile("encounter_codes", 2, "1-1-7", "3-1-1"));
        profiles.put("empty", profile("empty", 0));
        columns.addAll(List.of("patient", "encounter_codes", "empty"));
        TransformerRunContext context = context();
        selected.values().forEach(mapping -> {
            context.mappedColumns().put(mapping.getMappedColumn(), "node");
            context.config().getTransformer().addAll(mapping.getSelectedTransformers());
        });

        Optional<TransformerReview.Recheck> leftover = review.leftover(nodes, selected, sample(), context);

        assertEquals(List.of("encounter_codes"), leftover.orElseThrow().columns());
    }

    @Test
    void offersAColumnAnotherNodeUsesOnlyInPart() {
        // Admission source is the last of three codes joined in one cell, the other two stay unused
        drug(1L, "encounter_codes", Map.of("1-1-7", "7", "1-6-7", "7", "3-1-1", "1", "1-3-7", "7", "2-1-1", "1"),
                "1-1-7", "1-6-7", "3-1-1", "1-3-7", "2-1-1");
        drug(2L, "rx_a", Map.of("-", "No", "+", "Up"), "-", "+");
        TransformerRunContext context = context();
        selected.values().forEach(mapping -> {
            context.mappedColumns().put(mapping.getMappedColumn(), "node");
            context.config().getTransformer().addAll(mapping.getSelectedTransformers());
        });

        TransformerReview.Recheck leftover = review.leftover(nodes, selected, sample(), context).orElseThrow();

        assertEquals(List.of("encounter_codes"), leftover.columns());
        assertTrue(leftover.hint().contains("used by Medication.encounter_codes, which turns its 5 different values into 2 results"),
                leftover.hint());
    }

    private void drug(Long id, String column, Map<String, String> map, String... values) {
        LocalSchemaNodeWithPathDTO node = new LocalSchemaNodeWithPathDTO();
        node.setId(id);
        node.setName(column);
        node.setPath("Medication." + column);
        node.setDataType(new DataTypeDTO());
        nodes.add(node);
        ConnectorTransformerDTO mapper = new ConnectorTransformerDTO();
        mapper.setModuleName("builtin");
        mapper.setMethodName("Generic Value Mapper");
        mapper.setColumn(column);
        mapper.setHyperparams(new LinkedHashMap<>(Map.of("map", map)));
        selected.put(id, ConnectorSelectedTransformer.mapping(column, List.of(mapper)));
        profiles.put(column, profile(column, values.length, values));
        columns.add(column);
    }

    private static ColumnProfile profile(String column, int uniqueValues, String... values) {
        List<Map.Entry<String, Integer>> counts = Arrays.stream(values).<Map.Entry<String, Integer>>map(value -> Map.entry(value, 10)).toList();
        return new ColumnProfile(column, "TEXT", 100, 0, uniqueValues, null, null, null, null, null, null, null, counts, counts);
    }

    private TableSample sample() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patient", "p1");
        columns.forEach(column -> row.put(column, "-"));
        List<String> all = new ArrayList<>(List.of("patient"));
        columns.stream().filter(column -> !all.contains(column)).forEach(all::add);
        return new TableSample(all, List.of(row), null);
    }

    private TransformerRunContext context() {
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(new ArrayList<>());
        return new TransformerRunContext(config, "patient", "keycloak-user", profiles, new HashMap<>(), new ArrayList<>(), null);
    }
}
