package unit;

import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.Lang2QueryNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
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
}
