package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.QueryValidationBO;
import bio.cosy.feddb.core.api.query.item.ConnectingOperator;
import bio.cosy.feddb.core.api.query.item.IntervalPartDTO;
import bio.cosy.feddb.core.api.query.item.IntervalUnit;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.QueryItemHelper;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationship;
import bio.cosy.feddb.core.api.query.item.TimeAggregationMethod;
import bio.cosy.feddb.core.api.query.item.TimeFilterDTO;
import bio.cosy.feddb.core.api.query.item.ValueAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueMode;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// SQL shape and validation without a database
class QueryBuilderBOTest {

    private QueryBuilderBO builder;

    @BeforeEach
    void setUp() {
        QueryPredicateBO predicateBO = new QueryPredicateBO();
        SchemaNodeBO schemaNodeBO = mock(SchemaNodeBO.class);
        when(schemaNodeBO.findSchemaForQuery(any(), any())).thenAnswer(call -> {
            QueryItemDTO leaf = call.getArgument(0);
            return "string".equals(leaf.getOntologyId())
                    ? Map.of(DataTypes.STRING, List.of(3L))
                    : Map.of(DataTypes.INT, List.of(4L, 5L), DataTypes.STRING, List.of(6L));
        });

        QueryOccurrenceBO occurrenceBO = new QueryOccurrenceBO();
        occurrenceBO.schemaNodeBO = schemaNodeBO;
        occurrenceBO.predicateBO = predicateBO;
        QueryValidationBO validationBO = new QueryValidationBO();

        builder = new QueryBuilderBO();
        builder.validationBO = validationBO;
        builder.occurrenceBO = occurrenceBO;
    }

    @Test
    void plainLeafReadsOneColumn() {
        String sql = build(QueryItemHelper.andGroup(leaf("string", QueryOperatorTypes.EQUAL, "Diabetes Type 1")));

        assertEquals("SELECT DISTINCT patient_id FROM ((SELECT DISTINCT patient_id FROM "
                + "((SELECT patient_id, schema_node_id, visit_timestamp, id, occurrence_key, visit_id, import_schema_group_id "
                + "FROM (SELECT * FROM (SELECT patient_id, schema_node_id, visit_timestamp, id, "
                + "id AS occurrence_key, visit_id, import_schema_group_id, value_string AS agg_value FROM patient_data WHERE schema_node_id IN (3) AND value_string IS NOT NULL) "
                + "WHERE agg_value = 'Diabetes Type 1'))))) AS query_result", sql);
    }

    @Test
    void emptyQueryMatchesNothing() {
        assertEquals("SELECT DISTINCT patient_id FROM patient_data WHERE 1 = 0", build(QueryItemHelper.andGroup()));
        assertEquals("SELECT DISTINCT patient_id FROM patient_data WHERE 1 = 0", build(null));
    }

    @Test
    void groupsBecomeSetOperations() {
        QueryItemDTO tree = group(ConnectingOperator.AND,
                leaf("age", QueryOperatorTypes.BIGGER, "20"),
                group(ConnectingOperator.OR,
                        leaf("string", QueryOperatorTypes.EXISTS, null),
                        leaf("age", QueryOperatorTypes.SMALLER, "5")));

        String sql = build(tree);

        assertTrue(sql.contains(" INTERSECT "));
        assertTrue(sql.contains(" UNION "));
        assertBalanced(sql);
    }

    @Test
    void textOperatorSkipsNumericColumns() {
        String sql = build(leaf("age", QueryOperatorTypes.CONTAINS, "50%_o'k"));

        assertFalse(sql.contains("schema_node_id IN (4, 5)"));
        assertTrue(sql.contains("schema_node_id IN (6)"));
        assertTrue(sql.contains("ILIKE '%50\\%\\_o''k%' ESCAPE '\\'"));
    }

    @Test
    void numericComparisonParsesText() {
        String sql = build(leaf("age", QueryOperatorTypes.BIGGER, "26"));

        assertTrue(sql.contains("COALESCE(value_int, CASE WHEN btrim(value_string) ~ '^-?[0-9]+([.][0-9]+)?$'"));
        assertTrue(sql.contains("WHERE agg_value > 26"));
    }

    @Test
    void textComparisonStaysText() {
        String sql = build(leaf("string", QueryOperatorTypes.BIGGER, "abc"));

        assertFalse(sql.contains("::numeric"));
        assertTrue(sql.contains("WHERE agg_value > 'abc'"));
    }

    @Test
    void everyTimeShapeBuilds() {
        QueryItemDTO deltaWindow = leaf("age", QueryOperatorTypes.BIGGER, "0");
        deltaWindow.setValueMode(ValueMode.DELTA);
        deltaWindow.setDeltaInterval(List.of(new IntervalPartDTO(2, IntervalUnit.WEEKS), new IntervalPartDTO(3, IntervalUnit.DAYS)));
        deltaWindow.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        deltaWindow.setTimeFilter(new TimeFilterDTO(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)));

        QueryItemDTO windowAverage = leaf("age", QueryOperatorTypes.BIGGER_EQUAL, "30");
        windowAverage.setTimeAggregationMethod(TimeAggregationMethod.WITHIN);
        windowAverage.setTimeWindow(List.of(new IntervalPartDTO(40, IntervalUnit.DAYS)));
        windowAverage.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);

        QueryItemDTO windowCount = leaf("age", QueryOperatorTypes.BIGGER, "0");
        windowCount.setValueMode(ValueMode.DELTA);
        windowCount.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_MOST);
        windowCount.setTimeWindow(List.of(new IntervalPartDTO(70, IntervalUnit.DAYS)));
        windowCount.setTimeWindowCount(1);
        windowCount.setAtMost(2);

        QueryItemDTO changed = leaf("string", QueryOperatorTypes.CHANGED_TO, "Asthma");
        changed.setValueMode(ValueMode.DELTA);

        QueryItemDTO seriesAverage = leaf("age", QueryOperatorTypes.SMALLER, "40");
        seriesAverage.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);

        QueryItemDTO tree = group(ConnectingOperator.AND, deltaWindow, windowAverage, windowCount, changed, seriesAverage);
        tree.setTemporalRelations(List.of(
                relation(0, 1, TemporalRelationship.IN_THE_FOLLOWING, List.of(new IntervalPartDTO(1, IntervalUnit.MONTHS))),
                relation(2, 3, TemporalRelationship.SAME_GROUP, null)));

        String sql = build(tree);

        assertBalanced(sql);
        assertFalse(sql.contains("null"));
        assertTrue(sql.contains("interval '2 weeks 3 days'"));
        assertTrue(sql.contains("RANGE BETWEEN CURRENT ROW AND interval '70 days' FOLLOWING"));
        assertTrue(sql.contains("max_window_count <= 1"));
        assertTrue(sql.contains(") WHERE agg_value >= 30"));
        assertTrue(sql.contains("GROUP BY patient_id, schema_node_id) WHERE agg_value < 40"));
        assertTrue(sql.contains("agg_value = 'Asthma' AND agg_value IS DISTINCT FROM previous_value"));
        assertTrue(sql.contains("scoped.import_schema_group_id IS NOT NULL"));
        assertTrue(sql.contains("visit_timestamp > date '2020-01-01' AND visit_timestamp < date '2021-01-01'"));
    }

    @Test
    void rejectsInvalidCombinations() {
        QueryItemDTO absoluteChange = leaf("string", QueryOperatorTypes.HAS_CHANGED, null);
        assertRejected(absoluteChange, "requires valueMode DELTA");

        QueryItemDTO withinWithoutAverage = leaf("age", QueryOperatorTypes.BIGGER, "0");
        withinWithoutAverage.setTimeAggregationMethod(TimeAggregationMethod.WITHIN);
        withinWithoutAverage.setTimeWindow(List.of(new IntervalPartDTO(1, IntervalUnit.WEEKS)));
        assertRejected(withinWithoutAverage, "requires valueAggregationMethod AVERAGE");

        QueryItemDTO tooLong = leaf("age", QueryOperatorTypes.BIGGER, "0");
        tooLong.setValueMode(ValueMode.DELTA);
        tooLong.setDeltaInterval(List.of(new IntervalPartDTO(Integer.MAX_VALUE, IntervalUnit.WEEKS)));
        assertRejected(tooLong, "exceeds the maximum of 200 years");

        QueryItemDTO groupWithLeafField = group(ConnectingOperator.AND, leaf("age", QueryOperatorTypes.EXISTS, null));
        groupWithLeafField.setValueMode(ValueMode.DELTA);
        assertRejected(groupWithLeafField, "cannot also be a leaf");

        QueryItemDTO sameEndpoint = group(ConnectingOperator.AND,
                leaf("age", QueryOperatorTypes.EXISTS, null), leaf("string", QueryOperatorTypes.EXISTS, null));
        sameEndpoint.setTemporalRelations(List.of(relation(0, 0, TemporalRelationship.SAME_VISIT, null)));
        assertRejected(sameEndpoint, "two different conditions");

        QueryItemDTO injected = leaf("age", QueryOperatorTypes.BIGGER, "0; SELECT pg_sleep(1)");
        assertThrows(QueryRejectedException.class, () -> build(injected));
    }

    private String build(QueryItemDTO root) {
        QueryDTO dto = new QueryDTO();
        dto.setQuery(root);
        return builder.buildCountDistinctSql(dto, null);
    }

    private void assertRejected(QueryItemDTO root, String message) {
        QueryRejectedException rejected = assertThrows(QueryRejectedException.class, () -> build(root));
        assertTrue(rejected.getMessage().contains(message), rejected.getMessage());
    }

    private void assertBalanced(String sql) {
        int depth = 0;
        for (char c : sql.toCharArray()) {
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            assertTrue(depth >= 0, sql);
        }
        assertEquals(0, depth, sql);
    }

    private QueryItemDTO group(ConnectingOperator connectingOperator, QueryItemDTO... conditions) {
        QueryItemDTO group = new QueryItemDTO();
        group.setConnectingOperator(connectingOperator);
        group.setConditions(List.of(conditions));
        return group;
    }

    private QueryItemDTO leaf(String ontologyId, QueryOperatorTypes operator, String value) {
        QueryItemDTO leaf = new QueryItemDTO();
        leaf.setOntologyId(ontologyId);
        leaf.setOperator(operator);
        leaf.setValue(value);
        return leaf;
    }

    private TemporalRelationDTO relation(
            int source, int target, TemporalRelationship relationship, List<IntervalPartDTO> interval) {
        TemporalRelationDTO relation = new TemporalRelationDTO();
        relation.setSourceCondition(source);
        relation.setTargetCondition(target);
        relation.setRelationship(relationship);
        relation.setInterval(interval);
        return relation;
    }
}
