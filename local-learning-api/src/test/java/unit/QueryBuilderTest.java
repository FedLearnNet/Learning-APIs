package unit;

import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.Lang2QueryNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.TemporalRelationDTO;
import bio.cosy.feddb.local.api.query.LocalQueryDTO;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.query.QueryBuilderBO;
import bio.cosy.feddb.local.api.query.QueryRejectedException;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
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
    void testLang2QueryRejectsInvalidTemporalRelations() {
        Lang2QueryNodeDTO group = and(leaf("2001", "1001", "exists", null));
        group.setTemporalRelations(List.of(relation(0, 1, "same visit")));
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

    @Test
    @Order(22)
    void testTimeAggregationFirstAndLast() {
        // Patient 1 age: 25, 26, 27. Patient 2 age: 30, 31.
        Lang2QueryNodeDTO first = leaf("2002", "1002", "<", 26);
        first.setTimeAggregationMethod("first");
        String firstSql = queryBuilderBO.buildCountDistinctSql(queryTree(first));
        Assertions.assertTrue(firstSql.contains("ROW_NUMBER() OVER"));
        Assertions.assertTrue(firstSql.contains("ORDER BY event_time ASC, id ASC"));
        Assertions.assertTrue(firstSql.contains("rn = 1"));
        assertSqlExecutes(firstSql);
        Assertions.assertEquals(List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(first), List.of(1L, 5L))));

        Lang2QueryNodeDTO last = leaf("2002", "1002", ">", 30);
        last.setTimeAggregationMethod("last");
        String lastSql = queryBuilderBO.buildCountDistinctSql(queryTree(last));
        Assertions.assertTrue(lastSql.contains("ORDER BY event_time DESC, id DESC"));
        assertSqlExecutes(lastSql);
        Assertions.assertEquals(List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(last), List.of(1L, 5L))));
    }

    @Test
    @Order(23)
    void testValueAggregationAverageOverWholeSeries() {
        // Patient 1 avg age = 26, patient 2 avg age = 30.5.
        Lang2QueryNodeDTO average = leaf("2002", "1002", ">", 28);
        average.setValueAggregationMethod("average");
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(average));
        Assertions.assertTrue(sql.contains("GROUP BY"));
        Assertions.assertTrue(sql.contains("HAVING AVG(s.agg_value) > 28"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(average), List.of(1L, 5L))));
    }

    @Test
    @Order(24)
    void testTimeAggregationWithinXRequiresAverageAndUsesSlidingWindow() {
        Lang2QueryNodeDTO withinAvg = leaf("2002", "1002", ">=", 30);
        withinAvg.setTimeAggregationMethod("within 40 days");
        withinAvg.setValueAggregationMethod("average");
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(withinAvg));
        Assertions.assertTrue(sql.contains("interval '40 days'"));
        Assertions.assertTrue(sql.contains("HAVING AVG(r.agg_value) >= 30"));
        assertSqlExecutes(sql);
        // Patient 1's best 40-day window average tops out at 26.5; patient 2's reaches 30.5.
        Assertions.assertEquals(
                List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(withinAvg), List.of(1L, 5L))));
    }

    @Test
    @Order(25)
    void testTimeAggregationWithinCountWorksOnDelta() {
        // Patient 1 has two consecutive +1 age steps 59 days apart; patient 2 has only one step.
        Lang2QueryNodeDTO withinCount = leaf("2002", "1002", ">", 0);
        withinCount.setValueMode("delta");
        withinCount.setTimeAggregationMethod("within 70 days at least 2 times");
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(withinCount));
        Assertions.assertTrue(sql.contains("interval '70 days'"));
        Assertions.assertTrue(sql.contains("window_count >= 2"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(withinCount), List.of(1L, 5L))));

        Lang2QueryNodeDTO atMost = leaf("2002", "1002", ">", 0);
        atMost.setValueMode("delta");
        atMost.setTimeAggregationMethod("within 70 days at most 1 times");
        String atMostSql = queryBuilderBO.buildCountDistinctSql(queryTree(atMost));
        Assertions.assertTrue(atMostSql.contains("HAVING MAX(window_count) <= 1"));
        assertSqlExecutes(atMostSql);
    }

    @Test
    @Order(26)
    void testAggregationMethodsRejectInvalidCombinations() {
        // Not allowed on a group.
        Lang2QueryNodeDTO group = and(leaf("2002", "1002", ">", 0));
        group.setTimeAggregationMethod("first");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(group)));

        // "within X" plain requires value_aggregation_method = average.
        Lang2QueryNodeDTO withinNoAverage = leaf("2002", "1002", ">", 0);
        withinNoAverage.setTimeAggregationMethod("within 1 week");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(withinNoAverage)));

        // "within X at least/at most" is not allowed with value_aggregation_method = average.
        Lang2QueryNodeDTO withinCountAverage = leaf("2002", "1002", ">", 0);
        withinCountAverage.setTimeAggregationMethod("within 1 week at least 2 times");
        withinCountAverage.setValueAggregationMethod("average");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(withinCountAverage)));

        // value_aggregation_method = average requires value_mode = absolute.
        Lang2QueryNodeDTO averageOnDelta = leaf("2002", "1002", ">", 0);
        averageOnDelta.setValueMode("delta");
        averageOnDelta.setValueAggregationMethod("average");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(averageOnDelta)));

        // Aggregation methods do not combine with change operators yet.
        Lang2QueryNodeDTO changeWithAggregation = leaf("2001", "1001", "has_changed", null);
        changeWithAggregation.setValueMode("delta");
        changeWithAggregation.setTimeAggregationMethod("first");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(changeWithAggregation)));

        // Unsupported literal values are rejected, not ignored.
        Lang2QueryNodeDTO badTimeAggregation = leaf("2002", "1002", ">", 0);
        badTimeAggregation.setTimeAggregationMethod("yesterday");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(badTimeAggregation)));

        Lang2QueryNodeDTO badValueAggregation = leaf("2002", "1002", ">", 0);
        badValueAggregation.setValueAggregationMethod("median");
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(badValueAggregation)));
    }

    @Test
    @Order(27)
    void testTimeAggregationFirstLastCombineWithDeltaWindow() {
        // delta X ("delta 3 minutes") step source crossed with first/last: just needs to compile
        // and execute; the seed's monthly gaps mean no pair matches delta 3 minutes anyway.
        Lang2QueryNodeDTO firstOnDeltaWindow = leaf("2002", "1002", ">", 0);
        firstOnDeltaWindow.setValueMode("delta 3 minutes");
        firstOnDeltaWindow.setTimeAggregationMethod("first");
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(firstOnDeltaWindow));
        Assertions.assertTrue(sql.contains("later.numeric_value - earlier.numeric_value"));
        Assertions.assertTrue(sql.contains("rn = 1"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(firstOnDeltaWindow), List.of(1L, 5L))));
    }

    @Test
    @Order(28)
    void testAbsoluteComparisonParsesNumericDatatypeHintInsteadOfComparingText() {
        // Age (schema node 2) is INT: the hinted path must COALESCE to the typed column and
        // keep matching exactly like the unhinted comparison (regression safety).
        Lang2QueryNodeDTO ageHinted = leaf("2002", "1002", ">", 24);
        ageHinted.setDatatype("Float");
        String ageSql = queryBuilderBO.buildCountDistinctSql(queryTree(ageHinted));
        Assertions.assertTrue(ageSql.contains("COALESCE(value_int,"));
        assertSqlExecutes(ageSql);
        Assertions.assertEquals(
                List.of(1L, 2L), matchedPatientIds(queryBO.runQuery(queryTree(ageHinted), List.of(1L, 5L))));

        // Diagnosis (schema node 3) is STRING with non-numeric text ("Diabetes Type 2", ...).
        // Without the hint this used to compare lexicographically (e.g. "D..." > '0' is true,
        // the exact bug: text starting with a high character code looks "bigger" than a
        // number). With the hint it must only compare rows that actually parse as a number,
        // so unparseable diagnosis strings are excluded rather than wrongly matched.
        Lang2QueryNodeDTO diagnosisHinted = leaf("2001", "1001", ">", "0");
        diagnosisHinted.setDatatype("Float");
        String diagnosisSql = queryBuilderBO.buildCountDistinctSql(queryTree(diagnosisHinted));
        Assertions.assertTrue(diagnosisSql.contains("btrim(value_string) ~ '^-?[0-9]+([.][0-9]+)?$'"));
        Assertions.assertFalse(diagnosisSql.contains("value_string > '0'"));
        assertSqlExecutes(diagnosisSql);
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(diagnosisHinted), List.of(1L, 5L))));

        // Without the datatype hint, behavior is unchanged: a plain text comparison.
        Lang2QueryNodeDTO diagnosisUnhinted = leaf("2001", "1001", ">", "0");
        String unhintedSql = queryBuilderBO.buildCountDistinctSql(queryTree(diagnosisUnhinted));
        Assertions.assertTrue(unhintedSql.contains("value_string > '0'"));
        assertSqlExecutes(unhintedSql);
    }

    @Test
    @Order(29)
    void testFirstLastAggregationHonorsNumericDatatypeHint() {
        // Same hint-gated numeric parsing must apply on the first/last/within-count absolute
        // path (buildAggregatedColumnClause), not just the plain buildClause path.
        Lang2QueryNodeDTO firstHinted = leaf("2002", "1002", "<", 26);
        firstHinted.setDatatype("Float");
        firstHinted.setTimeAggregationMethod("first");
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(firstHinted));
        Assertions.assertTrue(sql.contains("COALESCE(value_int,"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(firstHinted), List.of(1L, 5L))));

        Lang2QueryNodeDTO diagnosisFirstHinted = leaf("2001", "1001", ">", "0");
        diagnosisFirstHinted.setDatatype("Float");
        diagnosisFirstHinted.setTimeAggregationMethod("first");
        String diagnosisSql = queryBuilderBO.buildCountDistinctSql(queryTree(diagnosisFirstHinted));
        Assertions.assertTrue(diagnosisSql.contains("btrim(value_string) ~"));
        assertSqlExecutes(diagnosisSql);
        Assertions.assertEquals(
                List.of(),
                matchedPatientIds(queryBO.runQuery(queryTree(diagnosisFirstHinted), List.of(1L, 5L))));
    }

    @Test
    @Order(30)
    @TestTransaction
    void testSameGroupCorrelatesBeforeFirstAggregation() {
        entityManager.createNativeQuery("""
                UPDATE patient_data SET import_schema_group_id = CASE id
                    WHEN 1 THEN 'AGE_DIAG_1' WHEN 4 THEN 'AGE_DIAG_1'
                    WHEN 2 THEN 'AGE_DIAG_2' WHEN 5 THEN 'AGE_DIAG_2'
                    WHEN 6 THEN 'AGE_ASTHMA_1' WHEN 8 THEN 'AGE_ASTHMA_1'
                    WHEN 7 THEN 'AGE_ONLY_2' ELSE import_schema_group_id END
                WHERE id IN (1, 2, 4, 5, 6, 7, 8)
                """).executeUpdate();
        Lang2QueryNodeDTO diagnosis = leaf("2001", "1001", "=", "Diabetes Type 2");
        Lang2QueryNodeDTO age = leaf("2002", "1002", ">", 24);
        age.setTimeAggregationMethod("first");
        Lang2QueryNodeDTO group = and(diagnosis, age);
        group.setTemporalRelations(List.of(relation(0, 1, "same group")));

        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(group));
        Assertions.assertTrue(sql.contains("import_schema_group_id"));
        Assertions.assertTrue(sql.contains("same_group_source"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(group), List.of(1L))));

        // The patient's global first age is 25 and fails >25. Correlation must happen
        // first, leaving age 26 in the Hypertension group as the scoped "first".
        Lang2QueryNodeDTO hypertension = leaf("2001", "1001", "=", "Hypertension");
        Lang2QueryNodeDTO scopedFirst = leaf("2002", "1002", ">", 25);
        scopedFirst.setTimeAggregationMethod("first");
        Lang2QueryNodeDTO correlateBeforeFirst = and(hypertension, scopedFirst);
        correlateBeforeFirst.setTemporalRelations(List.of(relation(0, 1, "same group")));
        Assertions.assertEquals(
                List.of(1L),
                matchedPatientIds(queryBO.runQuery(queryTree(correlateBeforeFirst), List.of(1L))));

        // Patient 2 has age 31, but it belongs to a different import group than the
        // Asthma row. Independent AND matches; same-group correlation must not.
        Lang2QueryNodeDTO asthma = leaf("2001", "1001", "=", "Asthma respiratory disease");
        Lang2QueryNodeDTO laterAge = leaf("2002", "1002", ">", 30);
        Lang2QueryNodeDTO independent = and(asthma, laterAge);
        Assertions.assertEquals(
                List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(independent), List.of(1L))));
        independent.setTemporalRelations(List.of(relation(0, 1, "same group")));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(independent), List.of(1L))));
    }

    @Test
    @Order(31)
    void testTemporalTimeVisitAndFollowingRelationships() {
        Lang2QueryNodeDTO height = leaf("2003", "1002", "=", 175);
        Lang2QueryNodeDTO weight = leaf("2004", "1002", "=", 70);

        Lang2QueryNodeDTO sameVisit = and(height, weight);
        sameVisit.setTemporalRelations(List.of(relation(0, 1, "same visit")));
        Assertions.assertEquals(
                List.of(3L), matchedPatientIds(queryBO.runQuery(queryTree(sameVisit), List.of(2L))));

        Lang2QueryNodeDTO sameTime = and(height, weight);
        sameTime.setTemporalRelations(List.of(relation(0, 1, "same time")));
        Assertions.assertEquals(
                List.of(3L), matchedPatientIds(queryBO.runQuery(queryTree(sameTime), List.of(2L))));

        Lang2QueryNodeDTO multiple = or(height, weight);
        multiple.setTemporalRelations(List.of(
                relation(0, 1, "same visit"),
                relation(0, 1, "same time")));
        Assertions.assertEquals(
                List.of(3L), matchedPatientIds(queryBO.runQuery(queryTree(multiple), List.of(2L))));

        Lang2QueryNodeDTO age25 = leaf("2002", "1002", "=", 25);
        Lang2QueryNodeDTO hypertension = leaf("2001", "1001", "=", "Hypertension");
        Lang2QueryNodeDTO following = and(age25, hypertension);
        following.setTemporalRelations(List.of(relation(0, 1, "in the following 40 days")));
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(following), List.of(1L))));

        following.setTemporalRelations(List.of(relation(0, 1, "in the following 20 days")));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(following), List.of(1L))));

        following.setTemporalRelations(List.of(relation(0, 1, "anytime after")));
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(following), List.of(1L))));

        Lang2QueryNodeDTO increasingAge = leaf("2002", "1002", ">", 0);
        increasingAge.setValueMode("delta");
        Lang2QueryNodeDTO deltaAtDiagnosis = and(increasingAge, hypertension);
        deltaAtDiagnosis.setTemporalRelations(List.of(relation(0, 1, "same time")));
        Assertions.assertEquals(
                List.of(1L),
                matchedPatientIds(queryBO.runQuery(queryTree(deltaAtDiagnosis), List.of(1L))));
    }

    @Test
    @Order(32)
    void testTemporalWithinCountsAndValidation() {
        Lang2QueryNodeDTO age25 = leaf("2002", "1002", "=", 25);
        Lang2QueryNodeDTO diagnosisExists = leaf("2001", "1001", "exists", null);
        Lang2QueryNodeDTO group = and(age25, diagnosisExists);

        TemporalRelationDTO twice = relation(0, 1, "within 40 days");
        twice.setAtLeast(2);
        group.setTemporalRelations(List.of(twice));
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(group), List.of(1L))));

        TemporalRelationDTO atMostOne = relation(0, 1, "within 40 days");
        atMostOne.setAtMost(1);
        group.setTemporalRelations(List.of(atMostOne));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(group), List.of(1L))));

        // Patient 1 has one diagnosis source followed by two age targets and another
        // source followed by one. At-most is universal, so the low-count source must not
        // hide the over-limit source. Patient 2 has only one following age target.
        Lang2QueryNodeDTO diagnoses = leaf("2001", "1001", "exists", null);
        Lang2QueryNodeDTO ages = leaf("2002", "1002", "exists", null);
        Lang2QueryNodeDTO universalAtMost = and(diagnoses, ages);
        TemporalRelationDTO followingAtMost = relation(0, 1, "in the following 70 days");
        followingAtMost.setAtMost(1);
        universalAtMost.setTemporalRelations(List.of(followingAtMost));
        Assertions.assertEquals(
                List.of(2L),
                matchedPatientIds(queryBO.runQuery(queryTree(universalAtMost), List.of(1L))));

        TemporalRelationDTO invalid = relation(0, 0, "same visit");
        group.setTemporalRelations(List.of(invalid));
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(group)));

        TemporalRelationDTO unknown = relation(0, 1, "nearby");
        group.setTemporalRelations(List.of(unknown));
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(group)));

        Lang2QueryNodeDTO nested = and(age25, and(diagnosisExists));
        nested.setTemporalRelations(List.of(relation(0, 1, "same group")));
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(nested)));
    }

    @Test
    @Order(33)
    @TestTransaction
    void testConditionOccurrenceCounts() {
        entityManager.createNativeQuery("""
                UPDATE patient_data SET import_schema_group_id = CASE id
                    WHEN 1 THEN 'AGE_DIAG_1' WHEN 4 THEN 'AGE_DIAG_1'
                    WHEN 2 THEN 'AGE_DIAG_2' WHEN 5 THEN 'AGE_DIAG_2'
                    WHEN 6 THEN 'AGE_ASTHMA_1' WHEN 8 THEN 'AGE_ASTHMA_1'
                    WHEN 7 THEN 'AGE_ONLY_2' ELSE import_schema_group_id END
                WHERE id IN (1, 2, 4, 5, 6, 7, 8)
                """).executeUpdate();

        // Patient 1 has one age above 26. Patient 2 has two. Everyone else has none.
        Lang2QueryNodeDTO age = leaf("2002", "1002", ">", 26);
        String omittedSql = queryBuilderBO.buildCountDistinctSql(queryTree(age));
        Assertions.assertFalse(omittedSql.contains("COUNT(DISTINCT occurrence_key)"));
        Assertions.assertEquals(
                List.of(1L, 2L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 2L))));

        age.setAtLeast(2);
        String atLeastSql = queryBuilderBO.buildCountDistinctSql(queryTree(age));
        Assertions.assertTrue(atLeastSql.contains("COUNT(DISTINCT occurrence_key) >= 2"));
        Assertions.assertFalse(atLeastSql.contains("patient_meta"));
        assertSqlExecutes(atLeastSql);
        Assertions.assertEquals(
                List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 2L))));

        age.setAtLeast(null);
        age.setAtMost(1);
        String atMostSql = queryBuilderBO.buildCountDistinctSql(queryTree(age));
        Assertions.assertTrue(atMostSql.contains("LEFT JOIN"));
        Assertions.assertTrue(atMostSql.contains("patient_meta"));
        Assertions.assertTrue(atMostSql.contains("COUNT(DISTINCT counted.occurrence_key) <= 1"));
        assertSqlExecutes(atMostSql);
        List<Long> atMostPatients = patientIds(atMostSql);
        Assertions.assertTrue(atMostPatients.contains(1L));
        Assertions.assertTrue(atMostPatients.contains(3L));
        Assertions.assertFalse(atMostPatients.contains(2L));

        age.setAtLeast(1);
        age.setAtMost(1);
        String rangeSql = queryBuilderBO.buildCountDistinctSql(queryTree(age));
        Assertions.assertTrue(rangeSql.contains("COUNT(DISTINCT occurrence_key) >= 1"));
        Assertions.assertTrue(rangeSql.contains("COUNT(DISTINCT occurrence_key) <= 1"));
        Assertions.assertFalse(rangeSql.contains("patient_meta"));
        assertSqlExecutes(rangeSql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 2L))));

        // The relation still has to hold. Patient 1 has three ages above 20 and a
        // same-group diabetes row, so at_least 2 matches and at_least 4 does not.
        Lang2QueryNodeDTO diabetes = leaf("2001", "1001", "=", "Diabetes Type 2");
        Lang2QueryNodeDTO countedAge = leaf("2002", "1002", ">", 20);
        countedAge.setAtLeast(2);
        Lang2QueryNodeDTO sameGroup = and(diabetes, countedAge);
        sameGroup.setTemporalRelations(List.of(relation(0, 1, "same group")));
        String sameGroupSql = queryBuilderBO.buildCountDistinctSql(queryTree(sameGroup));
        Assertions.assertTrue(sameGroupSql.contains("import_schema_group_id"));
        Assertions.assertTrue(sameGroupSql.contains("COUNT(DISTINCT occurrence_key) >= 2"));
        assertSqlExecutes(sameGroupSql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(sameGroup), List.of(1L))));
        countedAge.setAtLeast(4);
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(sameGroup), List.of(1L))));

        // Patient 2's age above 30 is in a different group from Asthma, so the count
        // alone must not satisfy the same-group relation.
        Lang2QueryNodeDTO asthma = leaf("2001", "1001", "=", "Asthma respiratory disease");
        Lang2QueryNodeDTO laterAge = leaf("2002", "1002", ">", 30);
        laterAge.setAtLeast(1);
        Lang2QueryNodeDTO asthmaGroup = and(asthma, laterAge);
        asthmaGroup.setTemporalRelations(List.of(relation(0, 1, "same group")));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(asthmaGroup), List.of(1L))));

        Lang2QueryNodeDTO booleanHint = leaf("2002", "1002", ">", 26);
        booleanHint.setDatatype("Boolean");
        String booleanSql = queryBuilderBO.buildCountDistinctSql(queryTree(booleanHint));
        Assertions.assertFalse(booleanSql.contains("COUNT(DISTINCT occurrence_key)"));
        assertSqlExecutes(booleanSql);
        Assertions.assertEquals(
                List.of(1L, 2L),
                matchedPatientIds(queryBO.runQuery(queryTree(booleanHint), List.of(1L, 2L))));

        Lang2QueryNodeDTO negative = leaf("2002", "1002", ">", 26);
        negative.setAtLeast(-1);
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(negative)));

        Lang2QueryNodeDTO inverted = leaf("2002", "1002", ">", 26);
        inverted.setAtLeast(5);
        inverted.setAtMost(1);
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(inverted)));

        Lang2QueryNodeDTO groupCount = and(leaf("2002", "1002", ">", 26));
        groupCount.setAtLeast(1);
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(groupCount)));

        Lang2QueryNodeDTO unknownDatatype = leaf("2002", "1002", ">", 26);
        unknownDatatype.setDatatype("Date");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(unknownDatatype)));
    }

    @Test
    @Order(34)
    void testNumericAndBooleanValuesAreValidatedAsLiterals() {
        Lang2QueryNodeDTO age = leaf("2002", "1002", "<", 28);
        String ageSql = queryBuilderBO.buildCountDistinctSql(queryTree(age));
        Assertions.assertTrue(ageSql.contains("value_int < 28"));
        assertSqlExecutes(ageSql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 5L))));

        Lang2QueryNodeDTO injectedAge = leaf("2002", "1002", "<", "28 OR pg_sleep(1)");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(injectedAge)));

        Lang2QueryNodeDTO injectedList = leaf("2002", "1002", "in", "25, 1 OR 1=1");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(injectedList)));

        Lang2QueryNodeDTO injectedBoolean = leaf("2008", "1004", "=", "true OR pg_sleep(1)");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(injectedBoolean)));

        Lang2QueryNodeDTO booleanLeaf = leaf("2008", "1004", "=", "true");
        String booleanSql = queryBuilderBO.buildCountDistinctSql(queryTree(booleanLeaf));
        Assertions.assertTrue(booleanSql.contains("value_boolean = true"));
        Assertions.assertFalse(booleanSql.contains("pg_sleep"));
        assertSqlExecutes(booleanSql);

        Lang2QueryNodeDTO firstInjected = leaf("2002", "1002", ">", "0; SELECT pg_sleep(1)");
        firstInjected.setTimeAggregationMethod("first");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(firstInjected)));
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

    private TemporalRelationDTO relation(int source, int target, String relationship) {
        TemporalRelationDTO relation = new TemporalRelationDTO();
        relation.setSourceCondition(source);
        relation.setTargetCondition(target);
        relation.setRelationship(relationship);
        return relation;
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
        return patientIds(response.getPatientMatchSql());
    }

    private List<Long> patientIds(String sql) {
        List<?> rows = entityManager.createNativeQuery(sql).getResultList();
        return rows.stream()
                .map(row -> ((Number) row).longValue())
                .sorted()
                .toList();
    }

}
