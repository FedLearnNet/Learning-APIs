package unit;

import bio.cosy.feddb.core.api.query.Lang2QueryNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.local.api.query.LocalQueryDTO;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.query.QueryBuilderBO;
import bio.cosy.feddb.local.api.query.QueryRejectedException;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QueryBuilderTest {

    @Inject
    QueryBuilderBO queryBuilderBO;

    @Inject
    QueryBO queryBO;

    @Inject
    QueryAO queryAO;

    @Inject
    EntityManager entityManager;

    @Test
    @Order(1)
    void testQueryGenerated() {
        final String result = "SELECT DISTINCT patient_id FROM (((SELECT DISTINCT patient_id FROM patient_data WHERE schema_node_id = 3 AND value_string = 'Diabetes Type 1'))) AS query_result";
        QueryOperatorDTO op = new QueryOperatorDTO();
        op.setOperator(QueryOperatorTypes.EQUAL);
        op.setValue("Diabetes Type 1");
        QueryItemDTO item = new QueryItemDTO();
        item.setOntologyId("2001");
        item.setDataTypeId("1001");
        item.setOperator(Collections.singletonList(op));
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setQuery(Collections.singletonList(item));


        String query = queryBuilderBO.buildCountDistinctSql(queryDTO);
        System.out.println("Generated Query: " + query);
        Assertions.assertEquals(result.trim(), query.trim());
        assertSqlExecutes(query);
    }

    @Test
    @Order(2)
    void testQueryResult() {
        // Query daibetes as string (patient 9)
        QueryDTO queryDTO = singleItemQuery("2008", "1001", QueryOperatorTypes.EQUAL, "Diabetes Type 1");
        QueryResultWrapperDTO response = queryBO.runQuery(queryDTO, List.of(5L));
        Assertions.assertEquals(1L, response.getResult().size());
        Assertions.assertEquals(5L, response.getResult().getFirst().getCohortId());
        Assertions.assertEquals(1L, patientCountForCohort(response, 5L));
        Assertions.assertEquals(List.of(9L), matchedPatientIds(response));

        // Query daibetes as categorical (patient 10)
        queryDTO = singleItemQuery("2008", "1008", QueryOperatorTypes.EQUAL, "Diabetes Type 2");
        response = queryBO.runQuery(queryDTO, List.of(5L));
        Assertions.assertEquals(1L, response.getResult().size());
        Assertions.assertEquals(5L, response.getResult().getFirst().getCohortId());
        Assertions.assertTrue(
            matchedPatientIds(response).contains(10L),
            "Patient 10 should be included in the result for diabetes query"
        );
    }

    @Test
    @Order(3)
    void testEmptyQueryReturnsNoResults() {
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setQuery(List.of());

        String query = queryBuilderBO.buildCountDistinctSql(queryDTO);
        Assertions.assertEquals(
                "SELECT DISTINCT patient_id FROM patient_data WHERE 1 = 0",
                query
        );
        assertSqlExecutes(query);

        QueryResultWrapperDTO response = queryBO.runQuery(queryDTO, List.of(1L, 5L));
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.getResult().isEmpty());
    }

    @Test
    @Order(4)
    void testExistsQueryReturnsMatchingPatients() {
        QueryDTO queryDTO = singleItemQuery("2008", "1001", QueryOperatorTypes.EXISTS, null);

        QueryResultWrapperDTO response = queryBO.runQuery(queryDTO, List.of(1L, 5L));
        Assertions.assertNotNull(response);
        Assertions.assertEquals(1, response.getResult().size());
        Assertions.assertEquals(
                List.of(5L),
                response.getResult().stream().map(r -> r.getCohortId()).sorted().toList()
        );
    }

    @Test
    @Order(5)
    void testIntersectQueryReturnsPatientMatchingAllItems() {
        QueryDTO queryDTO = new QueryDTO();
        // Two patiennts exist, one with age 25,26,27, diagnosis diabetes and one with age 30, 31, daignosis asthma
        // Find only the younger one
        queryDTO.setQuery(List.of(
                queryItem("2001", "1001", List.of(operator(QueryOperatorTypes.EXISTS, null))),
                queryItem("2002", "1002", List.of(operator(QueryOperatorTypes.SMALLER, "28")))
        ));

        QueryResultWrapperDTO response = queryBO.runQuery(queryDTO, List.of(1L, 5L));
        Assertions.assertNotNull(response);
        Assertions.assertEquals(1, response.getResult().size());
        Assertions.assertEquals(1L, response.getResult().getFirst().getCohortId());
        Assertions.assertEquals(1L, response.getResult().getFirst().getPatientCount());
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));

        // find both
        QueryDTO queryDTO2 = new QueryDTO();
        queryDTO2.setQuery(List.of(
                queryItem("2001", "1001", List.of(operator(QueryOperatorTypes.EXISTS, null))),
                queryItem("2002", "1002", List.of(operator(QueryOperatorTypes.SMALLER, "50")))
        ));
        QueryResultWrapperDTO response2 = queryBO.runQuery(queryDTO2, List.of(1L, 5L));
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(1L, response2.getResult().size());
        Assertions.assertEquals(1L, response2.getResult().getFirst().getCohortId());
        Assertions.assertEquals(2L, response2.getResult().getFirst().getPatientCount());
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(response2));
    }

    @Test
    @Order(6)
    void testUnknownOntologyRejectsQuery() {
        QueryDTO queryDTO = singleItemQuery("does-not-exist", "does-not-exist", QueryOperatorTypes.EQUAL, "foo");

        // An AND-conjunct whose schema node cannot be resolved must reject the query
        // instead of silently collapsing the INTERSECT to an empty result.
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryDTO));
    }

    @Test
    @Order(7)
    void testStoredQueryWithUnknownOntologyReturnsZeroMatches() {
        QueryDTO queryDTO = singleItemQuery("does-not-exist", "does-not-exist", QueryOperatorTypes.EQUAL, "foo");
        LocalQueryDTO storedQuery = new LocalQueryDTO();
        storedQuery.setId(42L);
        storedQuery.setQuery(queryDTO.getQuery());

        QueryResultWrapperDTO response = queryBO.runQuery(storedQuery, "user-without-permissions");

        Assertions.assertNotNull(response);
        Assertions.assertEquals(42L, response.getQueryId());
        Assertions.assertNotNull(response.getResult());
        Assertions.assertTrue(response.getResult().isEmpty());
    }

    @Test
    @Order(8)
    void testExistsClauseSupportsMultipleDatatypesPerOntologyInLocalSchema() {
        // date query
        QueryDTO dateQuery = singleItemQuery("2008", "1006", QueryOperatorTypes.EXISTS, null);
        String sqlDateQuery = queryBuilderBO.buildCountDistinctSql(dateQuery);
        System.out.println("SQL for date query: " + sqlDateQuery);
        Assertions.assertTrue(sqlDateQuery.contains("value_date IS NOT NULL"));
        assertSqlExecutes(sqlDateQuery);

        // date_time query
        QueryDTO dateTimeQuery = singleItemQuery("2008", "1007", QueryOperatorTypes.EXISTS, null);
        String sqlDateTimeQuery = queryBuilderBO.buildCountDistinctSql(dateTimeQuery);
        Assertions.assertTrue(sqlDateTimeQuery.contains("value_date_time IS NOT NULL"));
        assertSqlExecutes(sqlDateTimeQuery);

        // boolean query
        QueryDTO booleanQuery = singleItemQuery("2008", "1004", QueryOperatorTypes.EXISTS, null);
        String sqlBooleanQuery = queryBuilderBO.buildCountDistinctSql(booleanQuery);
        Assertions.assertTrue(sqlBooleanQuery.contains("value_boolean IS NOT NULL"));
        assertSqlExecutes(sqlBooleanQuery);

        // string query
        QueryDTO stringQuery = singleItemQuery("2008", "1001", QueryOperatorTypes.EXISTS, null);
        String sqlStringQuery = queryBuilderBO.buildCountDistinctSql(stringQuery);
        Assertions.assertTrue(sqlStringQuery.contains("value_string IS NOT NULL"));
        assertSqlExecutes(sqlStringQuery);
    }

    @Test
    @Order(9)
    void testLang2QueryAndMatchesLegacyIntersect() {
        QueryDTO lang2 = lang2Query(and(
                leaf("2001", "1001", "exists", null),
                leaf("2002", "1002", "<", 28)
        ));

        String lang2Sql = queryBuilderBO.buildCountDistinctSql(lang2);
        Assertions.assertTrue(lang2Sql.contains("INTERSECT"));
        assertSqlExecutes(lang2Sql);

        QueryResultWrapperDTO response = queryBO.runQuery(lang2, List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));
    }

    @Test
    @Order(10)
    void testLang2QueryOrUnionsPatients() {
        QueryDTO lang2 = lang2Query(or(
                leaf("2008", "1001", "=", "Diabetes Type 1"),
                leaf("2002", "1002", "<", 28)
        ));

        String sql = queryBuilderBO.buildCountDistinctSql(lang2);
        Assertions.assertTrue(sql.contains(" UNION "));
        assertSqlExecutes(sql);

        QueryResultWrapperDTO response = queryBO.runQuery(lang2, List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 9L), matchedPatientIds(response));
    }

    @Test
    @Order(11)
    void testLang2QueryNestedAndOr() {
        QueryDTO lang2 = lang2Query(and(
                leaf("2001", "1001", "exists", null),
                or(
                        leaf("2002", "1002", "<", 28),
                        leaf("2008", "1001", "=", "Diabetes Type 1")
                )
        ));

        String sql = queryBuilderBO.buildCountDistinctSql(lang2);
        Assertions.assertTrue(sql.contains("INTERSECT"));
        Assertions.assertTrue(sql.contains(" UNION "));
        assertSqlExecutes(sql);

        QueryResultWrapperDTO response = queryBO.runQuery(lang2, List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));
    }

    @Test
    @Order(12)
    void testLang2QueryRejectsTemporalRelations() {
        Lang2QueryNodeDTO group = and(leaf("2001", "1001", "exists", null));
        group.setTemporalRelations(List.of("same visit"));
        QueryDTO lang2 = lang2Query(group);

        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(lang2));
    }

    private QueryDTO lang2Query(Lang2QueryNodeDTO root) {
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setLang2Query(root);
        return queryDTO;
    }

    private Lang2QueryNodeDTO and(Lang2QueryNodeDTO... conditions) {
        return group("$and", conditions);
    }

    private Lang2QueryNodeDTO or(Lang2QueryNodeDTO... conditions) {
        return group("$or", conditions);
    }

    private Lang2QueryNodeDTO group(String connectingOperator, Lang2QueryNodeDTO... conditions) {
        Lang2QueryNodeDTO group = new Lang2QueryNodeDTO();
        group.setConnectingOperator(connectingOperator);
        group.setConditions(List.of(conditions));
        return group;
    }

    private Lang2QueryNodeDTO leaf(String ontologyId, String dataTypeId, String operator, Object value) {
        Lang2QueryNodeDTO leaf = new Lang2QueryNodeDTO();
        leaf.setOntologyId(ontologyId);
        leaf.setDataTypeId(dataTypeId);
        leaf.setOperator(operator);
        leaf.setValue(value);
        return leaf;
    }

    private QueryDTO singleItemQuery(String ontologyId, String dataTypeId, QueryOperatorTypes operator, String value) {
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setQuery(Collections.singletonList(queryItem(
                ontologyId,
                dataTypeId,
                Collections.singletonList(this.operator(operator, value))
        )));
        return queryDTO;
    }

    private QueryItemDTO queryItem(String ontologyId, String dataTypeId, List<QueryOperatorDTO> operators) {
        QueryItemDTO item = new QueryItemDTO();
        item.setOntologyId(ontologyId);
        item.setDataTypeId(dataTypeId);
        item.setOperator(operators);
        return item;
    }

    private QueryOperatorDTO operator(QueryOperatorTypes operator, String value) {
        QueryOperatorDTO op = new QueryOperatorDTO();
        op.setOperator(operator);
        op.setValue(value);
        return op;

    }

    private void assertSqlExecutes(String sql) {
        Assertions.assertDoesNotThrow(() -> queryAO.findPatientCountsByCohort(sql));
    }

    private long patientCountForCohort(QueryResultWrapperDTO response, Long cohortId) {
        return response.getResult().stream()
                .filter(result -> cohortId.equals(result.getCohortId()))
                .findFirst()
                .orElseThrow()
                .getPatientCount();
    }

    private List<Long> matchedPatientIds(QueryResultWrapperDTO response) {
        List<?> rows = entityManager.createNativeQuery(response.getPatientMatchSql()).getResultList();
        return rows.stream()
                .map(row -> ((Number) row).longValue())
                .sorted()
                .toList();
    }

}
