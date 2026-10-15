package unit;

import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
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
import java.util.Map;

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
        queryDTO.setQuery(Lang2QueryDocuments.andGroup(item));


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
        queryDTO.setQuery(Lang2QueryDocuments.andGroup());

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
        queryDTO.setQuery(Lang2QueryDocuments.andGroup(
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
        queryDTO2.setQuery(Lang2QueryDocuments.andGroup(
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
        QueryDTO lang2 = queryTree(and(
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
        QueryDTO lang2 = queryTree(or(
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
        QueryDTO lang2 = queryTree(and(
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
        QueryDTO lang2 = queryTree(group);

        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(lang2));
    }

    @Test
    @Order(13)
    void testTimeFilterAnytimeAddsNoVisitPredicate() {
        Lang2QueryNodeDTO anytime = leaf("2001", "1001", "exists", null);
        anytime.setTimeFilter("anytime");
        QueryDTO queryDTO = queryTree(anytime);

        String sql = queryBuilderBO.buildCountDistinctSql(queryDTO);
        Assertions.assertFalse(sql.contains("visit_timestamp"));
        assertSqlExecutes(sql);
    }

    @Test
    @Order(14)
    void testTimeFilterAfterBeforeAndRange() {
        Lang2QueryNodeDTO after = leaf("2001", "1001", "exists", null);
        after.setTimeFilter(Map.of("after", "2020-01-01"));
        String afterSql = queryBuilderBO.buildCountDistinctSql(queryTree(after));
        Assertions.assertTrue(afterSql.contains("visit_timestamp > date '2020-01-01'"));
        assertSqlExecutes(afterSql);

        Lang2QueryNodeDTO before = leaf("2001", "1001", "exists", null);
        before.setTimeFilter(Map.of("before", "2020-01-01"));
        String beforeSql = queryBuilderBO.buildCountDistinctSql(queryTree(before));
        Assertions.assertTrue(beforeSql.contains("visit_timestamp < date '2020-01-01'"));
        assertSqlExecutes(beforeSql);

        Lang2QueryNodeDTO range = leaf("2001", "1001", "exists", null);
        range.setTimeFilter(Map.of("after", "2020-01-01", "before", "2021-01-01"));
        String rangeSql = queryBuilderBO.buildCountDistinctSql(queryTree(range));
        Assertions.assertTrue(rangeSql.contains("visit_timestamp > date '2020-01-01'"));
        Assertions.assertTrue(rangeSql.contains("visit_timestamp < date '2021-01-01'"));
        assertSqlExecutes(rangeSql);
    }

    @Test
    @Order(15)
    void testTimeFilterRejectsInvalidValues() {
        Lang2QueryNodeDTO invalidText = leaf("2001", "1001", "exists", null);
        invalidText.setTimeFilter("last 30 days");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(invalidText)));

        Lang2QueryNodeDTO invalidDate = leaf("2001", "1001", "exists", null);
        invalidDate.setTimeFilter(Map.of("after", "01-01-2020"));
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(invalidDate)));
    }

    @Test
    @Order(16)
    void testAbsoluteValueModeMatchesRawAge() {
        Lang2QueryNodeDTO age = leaf("2002", "1002", "<", 28);
        age.setValueMode("absolute");

        QueryResultWrapperDTO response = queryBO.runQuery(queryTree(age), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));
    }

    @Test
    @Order(17)
    void testDeltaMatchesConsecutiveAgeIncrease() {
        Lang2QueryNodeDTO age = leaf("2002", "1002", ">", 0);
        age.setValueMode("delta");

        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(age));
        Assertions.assertTrue(sql.contains("COALESCE(value_int,"));
        Assertions.assertTrue(sql.contains("LAG(numeric_value)"));
        Assertions.assertFalse(sql.contains("interval "));
        assertSqlExecutes(sql);

        QueryResultWrapperDTO response = queryBO.runQuery(queryTree(age), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(response));

        Lang2QueryNodeDTO tooLarge = leaf("2002", "1002", ">", 2);
        tooLarge.setValueMode("delta");
        QueryResultWrapperDTO none = queryBO.runQuery(queryTree(tooLarge), List.of(1L, 5L));
        Assertions.assertEquals(List.of(), matchedPatientIds(none));
    }

    @Test
    @Order(18)
    void testDeltaWindowUsesVisitGap() {
        Lang2QueryNodeDTO withinWeek = leaf("2002", "1002", ">", 0);
        withinWeek.setValueMode("delta 7 days");
        String weekSql = queryBuilderBO.buildCountDistinctSql(queryTree(withinWeek));
        Assertions.assertTrue(weekSql.contains(
                "later.visit_timestamp <= earlier.visit_timestamp + interval '7 days'"));
        Assertions.assertFalse(weekSql.contains("LAG("));
        assertSqlExecutes(weekSql);
        QueryResultWrapperDTO week = queryBO.runQuery(queryTree(withinWeek), List.of(1L, 5L));
        Assertions.assertEquals(List.of(), matchedPatientIds(week));

        Lang2QueryNodeDTO withinFortyDays = leaf("2002", "1002", ">", 0);
        withinFortyDays.setValueMode("delta 40 days");
        String fortySql = queryBuilderBO.buildCountDistinctSql(queryTree(withinFortyDays));
        Assertions.assertTrue(fortySql.contains(
                "later.visit_timestamp <= earlier.visit_timestamp + interval '40 days'"));
        assertSqlExecutes(fortySql);
        QueryResultWrapperDTO forty = queryBO.runQuery(queryTree(withinFortyDays), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(forty));
    }

    @Test
    @Order(19)
    void testChangeOperatorsUseConsecutiveValues() {
        Lang2QueryNodeDTO changed = leaf("2001", "1001", "has_changed", null);
        changed.setValueMode("delta");
        QueryResultWrapperDTO changedResponse = queryBO.runQuery(queryTree(changed), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(changedResponse));

        Lang2QueryNodeDTO fromDiabetes = leaf("2001", "1001", "changed_from", "Diabetes Type 2");
        fromDiabetes.setValueMode("delta");
        QueryResultWrapperDTO fromResponse = queryBO.runQuery(queryTree(fromDiabetes), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(fromResponse));

        Lang2QueryNodeDTO toHypertension = leaf("2001", "1001", "changed_to", "Hypertension");
        toHypertension.setValueMode("delta");
        QueryResultWrapperDTO toResponse = queryBO.runQuery(queryTree(toHypertension), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(toResponse));
    }

    @Test
    @Order(20)
    void testValueModeRejectsGroupsWindowsAndNonNumericDelta() {
        Lang2QueryNodeDTO group = and(leaf("2002", "1002", ">", 0));
        group.setValueMode("delta");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(group)));

        Lang2QueryNodeDTO badInterval = leaf("2002", "1002", ">", 0);
        badInterval.setValueMode("delta yesterday");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(badInterval)));

        Lang2QueryNodeDTO absoluteChange = leaf("2001", "1001", "has_changed", null);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(absoluteChange)));

        Lang2QueryNodeDTO stringDelta = leaf("2001", "1001", ">", 0);
        stringDelta.setValueMode("delta");
        String stringSql = queryBuilderBO.buildCountDistinctSql(queryTree(stringDelta));
        Assertions.assertTrue(stringSql.contains("btrim(value_string)::numeric"));
        assertSqlExecutes(stringSql);
    }

    @Test
    @Order(21)
    void testDeltaParsesStringValuesWhenDatatypeIsNumeric() {
        Lang2QueryNodeDTO asFloat = leaf("2001", "1001", ">", 0);
        asFloat.setValueMode("delta 3 minutes");
        asFloat.setDatatype("Float");
        String floatSql = queryBuilderBO.buildCountDistinctSql(queryTree(asFloat));
        Assertions.assertTrue(floatSql.contains("btrim(value_string)::numeric"));
        Assertions.assertTrue(floatSql.contains("btrim(value_string) ~ '^-?[0-9]+([.][0-9]+)?$'"));
        Assertions.assertTrue(floatSql.contains("interval '3 minutes'"));
        assertSqlExecutes(floatSql);
        Assertions.assertEquals(List.of(), matchedPatientIds(queryBO.runQuery(queryTree(asFloat), List.of(1L, 5L))));

        Lang2QueryNodeDTO asInteger = leaf("2001", "1001", ">", 0);
        asInteger.setValueMode("delta");
        asInteger.setDatatype("Integer");
        String integerSql = queryBuilderBO.buildCountDistinctSql(queryTree(asInteger));
        Assertions.assertTrue(integerSql.contains("btrim(value_string) ~ '^-?[0-9]+$'"));
        Assertions.assertFalse(integerSql.contains("([.][0-9]+)?"));
        assertSqlExecutes(integerSql);

        Lang2QueryNodeDTO numericColumn = leaf("2002", "1002", ">", 0);
        numericColumn.setValueMode("delta");
        numericColumn.setDatatype("Float");
        String numericSql = queryBuilderBO.buildCountDistinctSql(queryTree(numericColumn));
        Assertions.assertTrue(numericSql.contains("COALESCE(value_int,"));
        Assertions.assertTrue(numericSql.contains("btrim(value_string)::numeric"));
        QueryResultWrapperDTO response = queryBO.runQuery(queryTree(numericColumn), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(response));
    }

    private QueryDTO queryTree(Lang2QueryNodeDTO root) {
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setQuery(root);
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
        queryDTO.setQuery(Lang2QueryDocuments.andGroup(queryItem(
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
