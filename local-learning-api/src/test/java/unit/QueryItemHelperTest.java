package unit;

import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.item.ConnectingOperator;
import bio.cosy.feddb.core.api.query.item.IntervalUnit;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.QueryItemHelper;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationship;
import bio.cosy.feddb.core.api.query.item.TimeAggregationMethod;
import bio.cosy.feddb.core.api.query.item.TimeFilterDTO;
import bio.cosy.feddb.core.api.query.item.ValueMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class QueryItemHelperTest {

    private static final String DOCUMENT = """
            {
              "connectingOperator": "OR",
              "temporalRelations": [{
                "sourceCondition": 0,
                "targetCondition": 1,
                "relationship": "IN_THE_FOLLOWING",
                "interval": [{"count": 2, "unit": "WEEKS"}],
                "atLeast": 2
              }],
              "conditions": [
                {"ontologyId": "2001", "dataTypeId": "1001", "operator": "EXISTS"},
                {"ontologyId": "2002", "dataTypeId": "1002", "operator": "SMALLER", "value": 28,
                 "valueMode": "DELTA", "deltaInterval": [{"count": 3, "unit": "DAYS"}],
                 "timeAggregationMethod": "FIRST", "timeFilter": {"after": "2020-01-01"}}
              ]
            }
            """;

    @Test
    void readsTreeWithEnumsAndFlattensLeaves() {
        QueryItemDTO root = QueryItemHelper.fromJson(DOCUMENT);

        Assertions.assertNotNull(root);
        Assertions.assertEquals(ConnectingOperator.OR, root.getConnectingOperator());
        TemporalRelationDTO relation = root.getTemporalRelations().getFirst();
        Assertions.assertEquals(TemporalRelationship.IN_THE_FOLLOWING, relation.getRelationship());
        Assertions.assertEquals(IntervalUnit.WEEKS, relation.getInterval().getFirst().getUnit());

        List<QueryItemDTO> leaves = QueryItemHelper.leavesOf(root);
        Assertions.assertEquals(2, leaves.size());
        QueryItemDTO delta = leaves.get(1);
        Assertions.assertEquals(QueryOperatorTypes.SMALLER, delta.getOperator());
        Assertions.assertEquals("28", delta.getValue());
        Assertions.assertEquals(ValueMode.DELTA, delta.getValueMode());
        Assertions.assertEquals(3, delta.getDeltaInterval().getFirst().getCount());
        Assertions.assertEquals(TimeAggregationMethod.FIRST, delta.getTimeAggregationMethod());
        Assertions.assertEquals(LocalDate.of(2020, 1, 1), delta.getTimeFilter().getAfter());
        Assertions.assertTrue(delta.isTimeDependent());
    }

    @Test
    void writesTreeWithoutHelperProperties() {
        QueryItemDTO leaf = new QueryItemDTO();
        leaf.setOntologyId("2001");
        leaf.setOperator(QueryOperatorTypes.EXISTS);

        String json = QueryItemHelper.toJson(QueryItemHelper.andGroup(leaf));

        Assertions.assertTrue(json.contains("\"connectingOperator\":\"AND\""));
        Assertions.assertTrue(json.contains("\"operator\":\"EXISTS\""));
        Assertions.assertFalse(json.contains("group"));
        Assertions.assertFalse(json.contains("timeDependent"));
        Assertions.assertFalse(json.contains("valueMode"));
        Assertions.assertEquals(QueryItemHelper.fromJson(json), QueryItemHelper.andGroup(leaf));
    }

    @Test
    void rejectsSnakeCaseAndUnknownFields() {
        Assertions.assertNull(QueryItemHelper.fromJson("{\"connecting_operator\":\"$and\",\"conditions\":[]}"));
        Assertions.assertNull(QueryItemHelper.fromJson("{\"ontologyId\":\"2001\",\"scale\":\"years\"}"));
        Assertions.assertNull(QueryItemHelper.fromJson(" "));
        Assertions.assertTrue(QueryItemHelper.leavesOf(null).isEmpty());
    }

    @Test
    void writesAndRejectsTimeFilterDates() {
        QueryItemDTO leaf = new QueryItemDTO();
        leaf.setOntologyId("2001");
        leaf.setOperator(QueryOperatorTypes.EXISTS);
        leaf.setTimeFilter(new TimeFilterDTO(LocalDate.of(2020, 1, 1), null));

        Assertions.assertTrue(QueryItemHelper.toJson(leaf).contains("\"timeFilter\":{\"after\":\"2020-01-01\"}"));
        Assertions.assertNull(QueryItemHelper.fromJson(
                "{\"ontologyId\":\"2001\",\"operator\":\"EXISTS\",\"timeFilter\":{\"after\":\"01-01-2020\"}}"));
    }
}
