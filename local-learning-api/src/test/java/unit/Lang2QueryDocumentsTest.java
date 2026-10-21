package unit;

import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.Lang2QueryNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.TemporalRelationDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class Lang2QueryDocumentsTest {

    @Test
    void readsLegacyItemArrayAsAndGroup() {
        QueryDTO dto = new QueryDTO();
        Lang2QueryDocuments.readInto(
                "[{\"ontologyId\":\"2001\",\"dataTypeId\":\"1001\",\"operator\":[{\"operator\":\"EQUAL\",\"value\":\"x\"}]}]",
                dto);

        Assertions.assertNotNull(dto.getQuery());
        Assertions.assertEquals("$and", dto.getQuery().getConnectingOperator());
        Assertions.assertEquals(1, dto.getQuery().getConditions().size());
        Assertions.assertEquals("2001", dto.getQuery().getConditions().getFirst().getOntologyId());
        List<QueryItemDTO> leaves = Lang2QueryDocuments.leavesOf(dto);
        Assertions.assertEquals(1, leaves.size());
        Assertions.assertEquals("2001", leaves.getFirst().getOntologyId());
    }

    @Test
    void readsLang2QueryGroupAndFlattensLeaves() {
        QueryDTO dto = new QueryDTO();
        Lang2QueryDocuments.readInto(
                """
                {"connecting_operator":"$or","conditions":[
                  {"ontology_id":"2001","data_type_id":"1001","operator":"exists"},
                  {"ontology_id":"2002","data_type_id":"1002","operator":"<","value":28}
                ]}
                """,
                dto);

        Assertions.assertNotNull(dto.getQuery());
        Assertions.assertEquals("$or", dto.getQuery().getConnectingOperator());
        List<QueryItemDTO> leaves = Lang2QueryDocuments.leavesOf(dto);
        Assertions.assertEquals(2, leaves.size());
        Assertions.assertEquals(QueryOperatorTypes.EXISTS, leaves.getFirst().getOperator().getFirst().getOperator());
        Assertions.assertEquals("28", leaves.get(1).getOperator().getFirst().getValue());
    }

    @Test
    void writesQueryTree() {
        Lang2QueryNodeDTO group = new Lang2QueryNodeDTO();
        group.setConnectingOperator("$and");
        Lang2QueryNodeDTO leaf = new Lang2QueryNodeDTO();
        leaf.setOntologyId("2001");
        leaf.setOperator("exists");
        group.setConditions(List.of(leaf));

        QueryDTO dto = new QueryDTO();
        dto.setQuery(group);

        String json = Lang2QueryDocuments.toQueryString(dto);
        Assertions.assertTrue(json.contains("\"connecting_operator\":\"$and\""));
        Assertions.assertTrue(json.contains("2001"));
        Assertions.assertFalse(json.startsWith("["));
    }

    @Test
    void readsAndWritesTypedTemporalRelations() {
        Lang2QueryNodeDTO root = Lang2QueryDocuments.parse("""
                {
                  "connecting_operator": "$and",
                  "temporal_relations": [{
                    "source_condition": 0,
                    "target_condition": 1,
                    "relationship": "same group",
                    "at_least": 2
                  }],
                  "conditions": [
                    {"ontology_id":"2001","operator":"exists"},
                    {"ontology_id":"2002","operator":"exists"}
                  ]
                }
                """);

        Assertions.assertNotNull(root);
        TemporalRelationDTO relation = root.getTemporalRelations().getFirst();
        Assertions.assertEquals(0, relation.getSourceCondition());
        Assertions.assertEquals(1, relation.getTargetCondition());
        Assertions.assertEquals("same group", relation.getRelationship());
        Assertions.assertEquals(2, relation.getAtLeast());

        String json = Lang2QueryDocuments.toQueryString(root);
        Assertions.assertTrue(json.contains("\"source_condition\":0"));
        Assertions.assertTrue(json.contains("\"target_condition\":1"));
        Assertions.assertTrue(json.contains("\"relationship\":\"same group\""));
    }

    @Test
    void paperOperatorSpellingsRoundTrip() {
        String json = """
                {"connecting_operator":"$and","conditions":[
                  {"ontology_id":"2001","operator":"=","value":"A"},
                  {"ontology_id":"2002","operator":"!=","value":"B"},
                  {"ontology_id":"2002","operator":">","value":1},
                  {"ontology_id":"2002","operator":"<","value":2},
                  {"ontology_id":"2002","operator":">=","value":3},
                  {"ontology_id":"2002","operator":"<=","value":4},
                  {"ontology_id":"2001","operator":"exists"}
                ]}
                """;
        Lang2QueryNodeDTO root = Lang2QueryDocuments.parse(json);
        List<QueryItemDTO> leaves = Lang2QueryDocuments.flattenLeaves(root);

        Assertions.assertEquals(List.of(
                QueryOperatorTypes.EQUAL,
                QueryOperatorTypes.NOT_EQUAL,
                QueryOperatorTypes.BIGGER,
                QueryOperatorTypes.SMALLER,
                QueryOperatorTypes.BIGGER_EQUAL,
                QueryOperatorTypes.SMALLER_EQUAL,
                QueryOperatorTypes.EXISTS),
                leaves.stream().map(leaf -> leaf.getOperator().getFirst().getOperator()).toList());

        String written = Lang2QueryDocuments.toQueryString(root);
        Assertions.assertTrue(written.contains("\"operator\":\"=\""));
        Assertions.assertTrue(written.contains("\"operator\":\"!=\""));
        Assertions.assertTrue(written.contains("\"operator\":\">\""));
        Assertions.assertTrue(written.contains("\"operator\":\"<\""));
        Assertions.assertTrue(written.contains("\"operator\":\">=\""));
        Assertions.assertTrue(written.contains("\"operator\":\"<=\""));
        Assertions.assertTrue(written.contains("\"operator\":\"exists\""));
        Assertions.assertFalse(written.contains("\"operator\":\"EQUAL\""));
        Assertions.assertFalse(written.contains("\"operator\":\"BIGGER\""));

        Assertions.assertEquals(QueryOperatorTypes.EQUAL, QueryOperatorTypes.fromOperator("=="));
        Assertions.assertEquals(QueryOperatorTypes.BIGGER, QueryOperatorTypes.fromOperator("BIGGER"));
        Assertions.assertEquals(QueryOperatorTypes.EXISTS, QueryOperatorTypes.fromOperator("Exists"));

        QueryOperatorDTO bigger = new QueryOperatorDTO();
        bigger.setOperator(QueryOperatorTypes.BIGGER);
        bigger.setValue("1");
        QueryItemDTO item = new QueryItemDTO();
        item.setOntologyId("2002");
        item.setOperator(List.of(bigger));
        Assertions.assertEquals(">", Lang2QueryDocuments.fromQueryItem(item).getOperator());
    }
}
