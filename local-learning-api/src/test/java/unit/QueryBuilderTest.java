package unit;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
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
import bio.cosy.feddb.local.api.query.LocalQueryDTO;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
import bio.cosy.feddb.local.api.query.builder.QueryBuilderBO;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QueryBuilderTest {

    private static final List<Long> COHORT_IDS = List.of(1L, 5L);

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
        final String result = "SELECT DISTINCT patient_id FROM ((SELECT DISTINCT patient_id FROM "
                + "((SELECT patient_id, schema_node_id, visit_timestamp, id, occurrence_key, visit_id, import_schema_group_id "
                + "FROM (SELECT * FROM (SELECT patient_id, schema_node_id, visit_timestamp, id, "
                + "id AS occurrence_key, visit_id, import_schema_group_id, value_string AS agg_value FROM patient_data WHERE schema_node_id IN (3) AND value_string IS NOT NULL) "
                + "WHERE agg_value = 'Diabetes Type 1'))))) AS query_result";
        QueryItemDTO item = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Diabetes Type 1");
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setQuery(QueryItemHelper.andGroup(item));


        String query = queryBuilderBO.buildCountDistinctSql(queryDTO, COHORT_IDS);
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
        queryDTO.setQuery(QueryItemHelper.andGroup());

        String query = queryBuilderBO.buildCountDistinctSql(queryDTO, COHORT_IDS);
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
        queryDTO.setQuery(QueryItemHelper.andGroup(
                leaf("2001", "1001", QueryOperatorTypes.EXISTS, null),
                leaf("2002", "1002", QueryOperatorTypes.SMALLER, "28")
        ));

        QueryResultWrapperDTO response = queryBO.runQuery(queryDTO, List.of(1L, 5L));
        Assertions.assertNotNull(response);
        Assertions.assertEquals(1, response.getResult().size());
        Assertions.assertEquals(1L, response.getResult().getFirst().getCohortId());
        Assertions.assertEquals(1L, response.getResult().getFirst().getPatientCount());
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));

        // find both
        QueryDTO queryDTO2 = new QueryDTO();
        queryDTO2.setQuery(QueryItemHelper.andGroup(
                leaf("2001", "1001", QueryOperatorTypes.EXISTS, null),
                leaf("2002", "1002", QueryOperatorTypes.SMALLER, "50")
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
                () -> queryBuilderBO.buildCountDistinctSql(queryDTO, COHORT_IDS));
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
        String sqlDateQuery = queryBuilderBO.buildCountDistinctSql(dateQuery, COHORT_IDS);
        System.out.println("SQL for date query: " + sqlDateQuery);
        Assertions.assertTrue(sqlDateQuery.contains("value_date AS agg_value"));
        assertSqlExecutes(sqlDateQuery);

        // date_time query
        QueryDTO dateTimeQuery = singleItemQuery("2008", "1007", QueryOperatorTypes.EXISTS, null);
        String sqlDateTimeQuery = queryBuilderBO.buildCountDistinctSql(dateTimeQuery, COHORT_IDS);
        Assertions.assertTrue(sqlDateTimeQuery.contains("value_date_time AS agg_value"));
        assertSqlExecutes(sqlDateTimeQuery);

        // boolean query
        QueryDTO booleanQuery = singleItemQuery("2008", "1004", QueryOperatorTypes.EXISTS, null);
        String sqlBooleanQuery = queryBuilderBO.buildCountDistinctSql(booleanQuery, COHORT_IDS);
        Assertions.assertTrue(sqlBooleanQuery.contains("value_boolean AS agg_value"));
        assertSqlExecutes(sqlBooleanQuery);

        // string query
        QueryDTO stringQuery = singleItemQuery("2008", "1001", QueryOperatorTypes.EXISTS, null);
        String sqlStringQuery = queryBuilderBO.buildCountDistinctSql(stringQuery, COHORT_IDS);
        Assertions.assertTrue(sqlStringQuery.contains("value_string AS agg_value"));
        assertSqlExecutes(sqlStringQuery);
    }

    @Test
    @Order(9)
    void testAndGroupIntersects() {
        QueryDTO lang2 = queryTree(and(
                leaf("2001", "1001", QueryOperatorTypes.EXISTS, null),
                leaf("2002", "1002", QueryOperatorTypes.SMALLER, 28)
        ));

        String lang2Sql = queryBuilderBO.buildCountDistinctSql(lang2, COHORT_IDS);
        Assertions.assertTrue(lang2Sql.contains("INTERSECT"));
        assertSqlExecutes(lang2Sql);

        QueryResultWrapperDTO response = queryBO.runQuery(lang2, List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));
    }

    @Test
    @Order(10)
    void testOrGroupUnionsPatients() {
        QueryDTO lang2 = queryTree(or(
                leaf("2008", "1001", QueryOperatorTypes.EQUAL, "Diabetes Type 1"),
                leaf("2002", "1002", QueryOperatorTypes.SMALLER, 28)
        ));

        String sql = queryBuilderBO.buildCountDistinctSql(lang2, COHORT_IDS);
        Assertions.assertTrue(sql.contains(" UNION "));
        assertSqlExecutes(sql);

        QueryResultWrapperDTO response = queryBO.runQuery(lang2, List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 9L), matchedPatientIds(response));
    }

    @Test
    @Order(11)
    void testNestedAndOr() {
        QueryDTO lang2 = queryTree(and(
                leaf("2001", "1001", QueryOperatorTypes.EXISTS, null),
                or(
                        leaf("2002", "1002", QueryOperatorTypes.SMALLER, 28),
                        leaf("2008", "1001", QueryOperatorTypes.EQUAL, "Diabetes Type 1")
                )
        ));

        String sql = queryBuilderBO.buildCountDistinctSql(lang2, COHORT_IDS);
        Assertions.assertTrue(sql.contains("INTERSECT"));
        Assertions.assertTrue(sql.contains(" UNION "));
        assertSqlExecutes(sql);

        QueryResultWrapperDTO response = queryBO.runQuery(lang2, List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));
    }

    @Test
    @Order(12)
    void testRejectsInvalidTemporalRelations() {
        QueryItemDTO group = and(leaf("2001", "1001", QueryOperatorTypes.EXISTS, null));
        group.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_VISIT, null)));
        QueryDTO lang2 = queryTree(group);

        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(lang2, COHORT_IDS));
    }

    @Test
    @Order(13)
    void testTimeFilterAnytimeAddsNoVisitPredicate() {
        QueryItemDTO anytime = leaf("2001", "1001", QueryOperatorTypes.EXISTS, null);
        anytime.setTimeFilter(null);
        QueryDTO queryDTO = queryTree(anytime);

        String sql = queryBuilderBO.buildCountDistinctSql(queryDTO, COHORT_IDS);
        Assertions.assertFalse(sql.contains("visit_timestamp >"));
        Assertions.assertFalse(sql.contains("visit_timestamp <"));
        Assertions.assertFalse(sql.contains("visit_timestamp IS NOT NULL"));
        assertSqlExecutes(sql);
    }

    @Test
    @Order(14)
    void testTimeFilterAfterBeforeAndRange() {
        QueryItemDTO after = leaf("2001", "1001", QueryOperatorTypes.EXISTS, null);
        after.setTimeFilter(new TimeFilterDTO(LocalDate.of(2020, 1, 1), null));
        String afterSql = queryBuilderBO.buildCountDistinctSql(queryTree(after), COHORT_IDS);
        Assertions.assertTrue(afterSql.contains("visit_timestamp > date '2020-01-01'"));
        assertSqlExecutes(afterSql);

        QueryItemDTO before = leaf("2001", "1001", QueryOperatorTypes.EXISTS, null);
        before.setTimeFilter(new TimeFilterDTO(null, LocalDate.of(2020, 1, 1)));
        String beforeSql = queryBuilderBO.buildCountDistinctSql(queryTree(before), COHORT_IDS);
        Assertions.assertTrue(beforeSql.contains("visit_timestamp < date '2020-01-01'"));
        assertSqlExecutes(beforeSql);

        QueryItemDTO range = leaf("2001", "1001", QueryOperatorTypes.EXISTS, null);
        range.setTimeFilter(new TimeFilterDTO(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)));
        String rangeSql = queryBuilderBO.buildCountDistinctSql(queryTree(range), COHORT_IDS);
        Assertions.assertTrue(rangeSql.contains("visit_timestamp > date '2020-01-01'"));
        Assertions.assertTrue(rangeSql.contains("visit_timestamp < date '2021-01-01'"));
        assertSqlExecutes(rangeSql);
    }

    @Test
    @Order(16)
    void testAbsoluteValueModeMatchesRawAge() {
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.SMALLER, 28);
        age.setValueMode(ValueMode.ABSOLUTE);

        QueryResultWrapperDTO response = queryBO.runQuery(queryTree(age), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(response));
    }

    @Test
    @Order(17)
    void testDeltaMatchesConsecutiveAgeIncrease() {
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        age.setValueMode(ValueMode.DELTA);

        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertTrue(sql.contains("COALESCE(value_int,"));
        Assertions.assertTrue(sql.contains("LAG(agg_value)"));
        Assertions.assertFalse(sql.contains("interval "));
        assertSqlExecutes(sql);

        QueryResultWrapperDTO response = queryBO.runQuery(queryTree(age), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(response));

        QueryItemDTO tooLarge = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 2);
        tooLarge.setValueMode(ValueMode.DELTA);
        QueryResultWrapperDTO none = queryBO.runQuery(queryTree(tooLarge), List.of(1L, 5L));
        Assertions.assertEquals(List.of(), matchedPatientIds(none));
    }

    @Test
    @Order(18)
    void testDeltaWindowUsesVisitGap() {
        QueryItemDTO withinWeek = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        withinWeek.setValueMode(ValueMode.DELTA);
        withinWeek.setDeltaInterval(interval("7 days"));
        String weekSql = queryBuilderBO.buildCountDistinctSql(queryTree(withinWeek), COHORT_IDS);
        Assertions.assertTrue(weekSql.contains(
                "later.visit_timestamp <= earlier.visit_timestamp + interval '7 days'"));
        Assertions.assertFalse(weekSql.contains("LAG("));
        assertSqlExecutes(weekSql);
        QueryResultWrapperDTO week = queryBO.runQuery(queryTree(withinWeek), List.of(1L, 5L));
        Assertions.assertEquals(List.of(), matchedPatientIds(week));

        QueryItemDTO withinFortyDays = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        withinFortyDays.setValueMode(ValueMode.DELTA);
        withinFortyDays.setDeltaInterval(interval("40 days"));
        String fortySql = queryBuilderBO.buildCountDistinctSql(queryTree(withinFortyDays), COHORT_IDS);
        Assertions.assertTrue(fortySql.contains(
                "later.visit_timestamp <= earlier.visit_timestamp + interval '40 days'"));
        assertSqlExecutes(fortySql);
        QueryResultWrapperDTO forty = queryBO.runQuery(queryTree(withinFortyDays), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(forty));
    }

    @Test
    @Order(19)
    void testChangeOperatorsUseConsecutiveValues() {
        QueryItemDTO changed = leaf("2001", "1001", QueryOperatorTypes.HAS_CHANGED, null);
        changed.setValueMode(ValueMode.DELTA);
        QueryResultWrapperDTO changedResponse = queryBO.runQuery(queryTree(changed), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(changedResponse));

        QueryItemDTO fromDiabetes = leaf("2001", "1001", QueryOperatorTypes.CHANGED_FROM, "Diabetes Type 2");
        fromDiabetes.setValueMode(ValueMode.DELTA);
        QueryResultWrapperDTO fromResponse = queryBO.runQuery(queryTree(fromDiabetes), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(fromResponse));

        QueryItemDTO toHypertension = leaf("2001", "1001", QueryOperatorTypes.CHANGED_TO, "Hypertension");
        toHypertension.setValueMode(ValueMode.DELTA);
        QueryResultWrapperDTO toResponse = queryBO.runQuery(queryTree(toHypertension), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L), matchedPatientIds(toResponse));
    }

    @Test
    @Order(20)
    void testValueModeRejectsGroupsWindowsAndNonNumericDelta() {
        QueryItemDTO group = and(leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0));
        group.setValueMode(ValueMode.DELTA);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(group), COHORT_IDS));

        QueryItemDTO badInterval = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        badInterval.setValueMode(ValueMode.DELTA);
        badInterval.setDeltaInterval(List.of(new IntervalPartDTO(0, IntervalUnit.DAYS)));
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(badInterval), COHORT_IDS));

        QueryItemDTO absoluteChange = leaf("2001", "1001", QueryOperatorTypes.HAS_CHANGED, null);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(absoluteChange), COHORT_IDS));

        QueryItemDTO stringDelta = leaf("2001", "1001", QueryOperatorTypes.BIGGER, 0);
        stringDelta.setValueMode(ValueMode.DELTA);
        String stringSql = queryBuilderBO.buildCountDistinctSql(queryTree(stringDelta), COHORT_IDS);
        Assertions.assertTrue(stringSql.contains("btrim(value_string)::numeric"));
        assertSqlExecutes(stringSql);
    }

    @Test
    @Order(21)
    void testDeltaParsesStringValues() {
        QueryItemDTO stringColumn = leaf("2001", "1001", QueryOperatorTypes.BIGGER, 0);
        stringColumn.setValueMode(ValueMode.DELTA);
        stringColumn.setDeltaInterval(interval("3 minutes"));
        String stringSql = queryBuilderBO.buildCountDistinctSql(queryTree(stringColumn), COHORT_IDS);
        Assertions.assertTrue(stringSql.contains("btrim(value_string)::numeric"));
        Assertions.assertTrue(stringSql.contains("btrim(value_string) ~ '^-?[0-9]+([.][0-9]+)?$'"));
        Assertions.assertTrue(stringSql.contains("interval '3 minutes'"));
        assertSqlExecutes(stringSql);
        Assertions.assertEquals(List.of(), matchedPatientIds(queryBO.runQuery(queryTree(stringColumn), List.of(1L, 5L))));

        QueryItemDTO numericColumn = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        numericColumn.setValueMode(ValueMode.DELTA);
        String numericSql = queryBuilderBO.buildCountDistinctSql(queryTree(numericColumn), COHORT_IDS);
        Assertions.assertTrue(numericSql.contains("COALESCE(value_int,"));
        Assertions.assertTrue(numericSql.contains("btrim(value_string)::numeric"));
        QueryResultWrapperDTO response = queryBO.runQuery(queryTree(numericColumn), List.of(1L, 5L));
        Assertions.assertEquals(List.of(1L, 2L), matchedPatientIds(response));
    }

    @Test
    @Order(22)
    void testTimeAggregationFirstAndLast() {
        // Patient 1 age: 25, 26, 27. Patient 2 age: 30, 31.
        QueryItemDTO first = leaf("2002", "1002", QueryOperatorTypes.SMALLER, 26);
        first.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        String firstSql = queryBuilderBO.buildCountDistinctSql(queryTree(first), COHORT_IDS);
        Assertions.assertTrue(firstSql.contains("ROW_NUMBER() OVER"));
        Assertions.assertTrue(firstSql.contains("ORDER BY visit_timestamp ASC, id ASC"));
        Assertions.assertTrue(firstSql.contains("rn = 1"));
        assertSqlExecutes(firstSql);
        Assertions.assertEquals(List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(first), List.of(1L, 5L))));

        QueryItemDTO last = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 30);
        last.setTimeAggregationMethod(TimeAggregationMethod.LAST);
        String lastSql = queryBuilderBO.buildCountDistinctSql(queryTree(last), COHORT_IDS);
        Assertions.assertTrue(lastSql.contains("ORDER BY visit_timestamp DESC, id DESC"));
        assertSqlExecutes(lastSql);
        Assertions.assertEquals(List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(last), List.of(1L, 5L))));
    }

    @Test
    @Order(23)
    void testValueAggregationAverageOverWholeSeries() {
        // Patient 1 avg age = 26, patient 2 avg age = 30.5.
        QueryItemDTO average = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 28);
        average.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(average), COHORT_IDS);
        Assertions.assertTrue(sql.contains("GROUP BY"));
        Assertions.assertTrue(sql.contains("GROUP BY patient_id, schema_node_id) WHERE agg_value > 28"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(average), List.of(1L, 5L))));
    }

    @Test
    @Order(24)
    void testTimeAggregationWithinXRequiresAverageAndUsesSlidingWindow() {
        QueryItemDTO withinAvg = leaf("2002", "1002", QueryOperatorTypes.BIGGER_EQUAL, 30);
        withinAvg.setTimeAggregationMethod(TimeAggregationMethod.WITHIN);
        withinAvg.setTimeWindow(interval("40 days"));
        withinAvg.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(withinAvg), COHORT_IDS);
        Assertions.assertTrue(sql.contains("interval '40 days'"));
        Assertions.assertTrue(sql.contains(") WHERE agg_value >= 30"));
        assertSqlExecutes(sql);
        // Patient 1's best 40-day window average tops out at 26.5; patient 2's reaches 30.5.
        Assertions.assertEquals(
                List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(withinAvg), List.of(1L, 5L))));
    }

    @Test
    @Order(25)
    void testTimeAggregationWithinCountWorksOnDelta() {
        // Patient 1 has two consecutive +1 age steps 59 days apart; patient 2 has only one step.
        QueryItemDTO withinCount = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        withinCount.setValueMode(ValueMode.DELTA);
        withinCount.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_LEAST);
        withinCount.setTimeWindow(interval("70 days"));
        withinCount.setTimeWindowCount(2);
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(withinCount), COHORT_IDS);
        Assertions.assertTrue(sql.contains("interval '70 days'"));
        Assertions.assertTrue(sql.contains("window_count >= 2"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(withinCount), List.of(1L, 5L))));

        QueryItemDTO atMost = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        atMost.setValueMode(ValueMode.DELTA);
        atMost.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_MOST);
        atMost.setTimeWindow(interval("70 days"));
        atMost.setTimeWindowCount(1);
        String atMostSql = queryBuilderBO.buildCountDistinctSql(queryTree(atMost), COHORT_IDS);
        Assertions.assertTrue(atMostSql.contains("max_window_count <= 1"));
        assertSqlExecutes(atMostSql);
    }

    @Test
    @Order(26)
    void testAggregationMethodsRejectInvalidCombinations() {
        // Not allowed on a group.
        QueryItemDTO group = and(leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0));
        group.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(group), COHORT_IDS));

        // "within X" plain requires value_aggregation_method = average.
        QueryItemDTO withinNoAverage = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        withinNoAverage.setTimeAggregationMethod(TimeAggregationMethod.WITHIN);
        withinNoAverage.setTimeWindow(interval("1 week"));
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(withinNoAverage), COHORT_IDS));

        // "within X at least/at most" is not allowed with value_aggregation_method = average.
        QueryItemDTO withinCountAverage = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        withinCountAverage.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_LEAST);
        withinCountAverage.setTimeWindow(interval("1 week"));
        withinCountAverage.setTimeWindowCount(2);
        withinCountAverage.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(withinCountAverage), COHORT_IDS));

        // value_aggregation_method = average requires value_mode = absolute.
        QueryItemDTO averageOnDelta = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        averageOnDelta.setValueMode(ValueMode.DELTA);
        averageOnDelta.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(averageOnDelta), COHORT_IDS));

        // Aggregation methods do not combine with change operators yet.
        QueryItemDTO changeWithAggregation = leaf("2001", "1001", QueryOperatorTypes.HAS_CHANGED, null);
        changeWithAggregation.setValueMode(ValueMode.DELTA);
        changeWithAggregation.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(changeWithAggregation), COHORT_IDS));

        QueryItemDTO missingWindowCount = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        missingWindowCount.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_LEAST);
        missingWindowCount.setTimeWindow(interval("1 week"));
        Assertions.assertThrows(QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(missingWindowCount), COHORT_IDS));
    }

    @Test
    @Order(27)
    void testTimeAggregationFirstLastCombineWithDeltaWindow() {
        // delta X ("delta 3 minutes") step source crossed with first/last: just needs to compile
        // and execute; the seed's monthly gaps mean no pair matches delta 3 minutes anyway.
        QueryItemDTO firstOnDeltaWindow = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        firstOnDeltaWindow.setValueMode(ValueMode.DELTA);
        firstOnDeltaWindow.setDeltaInterval(interval("3 minutes"));
        firstOnDeltaWindow.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(firstOnDeltaWindow), COHORT_IDS);
        Assertions.assertTrue(sql.contains("agg_value - previous_value"));
        Assertions.assertTrue(sql.contains("rn = 1"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(firstOnDeltaWindow), List.of(1L, 5L))));
    }

    @Test
    @Order(28)
    void testAbsoluteComparisonWithNumberParsesInsteadOfComparingText() {
        // age (schema node 2) is INT, text holding a number counts as well
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 24);
        String ageSql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertTrue(ageSql.contains("COALESCE(value_int,"));
        assertSqlExecutes(ageSql);
        Assertions.assertEquals(
                List.of(1L, 2L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 5L))));

        // diagnosis (schema node 3) is STRING without numbers; compared as text "Diabetes..." > '0' would match
        QueryItemDTO diagnosis = leaf("2001", "1001", QueryOperatorTypes.BIGGER, "0");
        String diagnosisSql = queryBuilderBO.buildCountDistinctSql(queryTree(diagnosis), COHORT_IDS);
        Assertions.assertTrue(diagnosisSql.contains("btrim(value_string) ~ '^-?[0-9]+([.][0-9]+)?$'"));
        Assertions.assertFalse(diagnosisSql.contains("agg_value > '0'"));
        assertSqlExecutes(diagnosisSql);
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(diagnosis), List.of(1L, 5L))));

        // a value that is not a number stays a text comparison
        QueryItemDTO diagnosisText = leaf("2001", "1001", QueryOperatorTypes.BIGGER, "A");
        String textSql = queryBuilderBO.buildCountDistinctSql(queryTree(diagnosisText), COHORT_IDS);
        Assertions.assertTrue(textSql.contains("agg_value > 'A'"));
        assertSqlExecutes(textSql);
    }

    @Test
    @Order(29)
    void testFirstLastAggregationParsesNumbers() {
        // numeric parsing also applies on the first/last path
        QueryItemDTO first = leaf("2002", "1002", QueryOperatorTypes.SMALLER, 26);
        first.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(first), COHORT_IDS);
        Assertions.assertTrue(sql.contains("COALESCE(value_int,"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(first), List.of(1L, 5L))));

        QueryItemDTO diagnosisFirst = leaf("2001", "1001", QueryOperatorTypes.BIGGER, "0");
        diagnosisFirst.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        String diagnosisSql = queryBuilderBO.buildCountDistinctSql(queryTree(diagnosisFirst), COHORT_IDS);
        Assertions.assertTrue(diagnosisSql.contains("btrim(value_string) ~"));
        assertSqlExecutes(diagnosisSql);
        Assertions.assertEquals(
                List.of(),
                matchedPatientIds(queryBO.runQuery(queryTree(diagnosisFirst), List.of(1L, 5L))));
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
        QueryItemDTO diagnosis = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Diabetes Type 2");
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 24);
        age.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        QueryItemDTO group = and(diagnosis, age);
        group.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_GROUP, null)));

        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(group), COHORT_IDS);
        Assertions.assertTrue(sql.contains("import_schema_group_id"));
        Assertions.assertTrue(sql.contains("same_group_source"));
        assertSqlExecutes(sql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(group), List.of(1L))));

        // The patient's global first age is 25 and fails >25. Correlation must happen
        // first, leaving age 26 in the Hypertension group as the scoped "first".
        QueryItemDTO hypertension = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Hypertension");
        QueryItemDTO scopedFirst = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 25);
        scopedFirst.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        QueryItemDTO correlateBeforeFirst = and(hypertension, scopedFirst);
        correlateBeforeFirst.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_GROUP, null)));
        Assertions.assertEquals(
                List.of(1L),
                matchedPatientIds(queryBO.runQuery(queryTree(correlateBeforeFirst), List.of(1L))));

        // Patient 2 has age 31, but it belongs to a different import group than the
        // Asthma row. Independent AND matches; same-group correlation must not.
        QueryItemDTO asthma = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Asthma respiratory disease");
        QueryItemDTO laterAge = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 30);
        QueryItemDTO independent = and(asthma, laterAge);
        Assertions.assertEquals(
                List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(independent), List.of(1L))));
        independent.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_GROUP, null)));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(independent), List.of(1L))));
    }

    @Test
    @Order(31)
    void testTemporalTimeVisitAndFollowingRelationships() {
        QueryItemDTO height = leaf("2003", "1002", QueryOperatorTypes.EQUAL, 175);
        QueryItemDTO weight = leaf("2004", "1002", QueryOperatorTypes.EQUAL, 70);

        QueryItemDTO sameVisit = and(height, weight);
        sameVisit.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_VISIT, null)));
        Assertions.assertEquals(
                List.of(3L), matchedPatientIds(queryBO.runQuery(queryTree(sameVisit), List.of(2L))));

        QueryItemDTO sameTime = and(height, weight);
        sameTime.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_TIME, null)));
        Assertions.assertEquals(
                List.of(3L), matchedPatientIds(queryBO.runQuery(queryTree(sameTime), List.of(2L))));

        QueryItemDTO multiple = or(height, weight);
        multiple.setTemporalRelations(List.of(
                relation(0, 1, TemporalRelationship.SAME_VISIT, null),
                relation(0, 1, TemporalRelationship.SAME_TIME, null)));
        Assertions.assertEquals(
                List.of(3L), matchedPatientIds(queryBO.runQuery(queryTree(multiple), List.of(2L))));

        QueryItemDTO age25 = leaf("2002", "1002", QueryOperatorTypes.EQUAL, 25);
        QueryItemDTO hypertension = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Hypertension");
        QueryItemDTO following = and(age25, hypertension);
        following.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.IN_THE_FOLLOWING, interval("40 days"))));
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(following), List.of(1L))));

        following.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.IN_THE_FOLLOWING, interval("20 days"))));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(following), List.of(1L))));

        following.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.ANYTIME_AFTER, null)));
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(following), List.of(1L))));

        QueryItemDTO increasingAge = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        increasingAge.setValueMode(ValueMode.DELTA);
        QueryItemDTO deltaAtDiagnosis = and(increasingAge, hypertension);
        deltaAtDiagnosis.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_TIME, null)));
        Assertions.assertEquals(
                List.of(1L),
                matchedPatientIds(queryBO.runQuery(queryTree(deltaAtDiagnosis), List.of(1L))));
    }

    @Test
    @Order(32)
    void testTemporalWithinCountsAndValidation() {
        QueryItemDTO age25 = leaf("2002", "1002", QueryOperatorTypes.EQUAL, 25);
        QueryItemDTO diagnosisExists = leaf("2001", "1001", QueryOperatorTypes.EXISTS, null);
        QueryItemDTO group = and(age25, diagnosisExists);

        TemporalRelationDTO twice = relation(0, 1, TemporalRelationship.WITHIN, interval("40 days"));
        twice.setAtLeast(2);
        group.setTemporalRelations(List.of(twice));
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(group), List.of(1L))));

        TemporalRelationDTO atMostOne = relation(0, 1, TemporalRelationship.WITHIN, interval("40 days"));
        atMostOne.setAtMost(1);
        group.setTemporalRelations(List.of(atMostOne));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(group), List.of(1L))));

        // Patient 1 has one diagnosis source followed by two age targets and another
        // source followed by one. At-most is universal, so the low-count source must not
        // hide the over-limit source. Patient 2 has only one following age target.
        QueryItemDTO diagnoses = leaf("2001", "1001", QueryOperatorTypes.EXISTS, null);
        QueryItemDTO ages = leaf("2002", "1002", QueryOperatorTypes.EXISTS, null);
        QueryItemDTO universalAtMost = and(diagnoses, ages);
        TemporalRelationDTO followingAtMost = relation(0, 1, TemporalRelationship.IN_THE_FOLLOWING, interval("70 days"));
        followingAtMost.setAtMost(1);
        universalAtMost.setTemporalRelations(List.of(followingAtMost));
        Assertions.assertEquals(
                List.of(2L),
                matchedPatientIds(queryBO.runQuery(queryTree(universalAtMost), List.of(1L))));

        TemporalRelationDTO invalid = relation(0, 0, TemporalRelationship.SAME_VISIT, null);
        group.setTemporalRelations(List.of(invalid));
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(group), COHORT_IDS));

        TemporalRelationDTO unknown = relation(0, 1, null, null);
        group.setTemporalRelations(List.of(unknown));
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(group), COHORT_IDS));

        QueryItemDTO nested = and(age25, and(diagnosisExists));
        nested.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_GROUP, null)));
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(nested), COHORT_IDS));
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
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 26);
        String omittedSql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertFalse(omittedSql.contains("COUNT(DISTINCT counted.occurrence_key)"));
        Assertions.assertEquals(
                List.of(1L, 2L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 2L))));

        age.setAtLeast(2);
        String atLeastSql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertTrue(atLeastSql.contains("COUNT(DISTINCT counted.occurrence_key) >= 2"));
        assertSqlExecutes(atLeastSql);
        Assertions.assertEquals(
                List.of(2L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 2L))));

        age.setAtLeast(null);
        age.setAtMost(1);
        String atMostSql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
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
        String rangeSql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertTrue(rangeSql.contains("COUNT(DISTINCT counted.occurrence_key) >= 1 AND COUNT(DISTINCT counted.occurrence_key) <= 1"));
        assertSqlExecutes(rangeSql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 2L))));

        // The relation still has to hold. Patient 1 has three ages above 20 and a
        // same-group diabetes row, so at_least 2 matches and at_least 4 does not.
        QueryItemDTO diabetes = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Diabetes Type 2");
        QueryItemDTO countedAge = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 20);
        countedAge.setAtLeast(2);
        QueryItemDTO sameGroup = and(diabetes, countedAge);
        sameGroup.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_GROUP, null)));
        String sameGroupSql = queryBuilderBO.buildCountDistinctSql(queryTree(sameGroup), COHORT_IDS);
        Assertions.assertTrue(sameGroupSql.contains("import_schema_group_id"));
        Assertions.assertTrue(sameGroupSql.contains("COUNT(DISTINCT counted.occurrence_key) >= 2"));
        assertSqlExecutes(sameGroupSql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(sameGroup), List.of(1L))));
        countedAge.setAtLeast(4);
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(sameGroup), List.of(1L))));

        // Patient 2's age above 30 is in a different group from Asthma, so the count
        // alone must not satisfy the same-group relation.
        QueryItemDTO asthma = leaf("2001", "1001", QueryOperatorTypes.EQUAL, "Asthma respiratory disease");
        QueryItemDTO laterAge = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 30);
        laterAge.setAtLeast(1);
        QueryItemDTO asthmaGroup = and(asthma, laterAge);
        asthmaGroup.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.SAME_GROUP, null)));
        Assertions.assertEquals(
                List.of(), matchedPatientIds(queryBO.runQuery(queryTree(asthmaGroup), List.of(1L))));

        QueryItemDTO negative = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 26);
        negative.setAtLeast(-1);
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(negative), COHORT_IDS));

        QueryItemDTO inverted = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 26);
        inverted.setAtLeast(5);
        inverted.setAtMost(1);
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(inverted), COHORT_IDS));

        QueryItemDTO groupCount = and(leaf("2002", "1002", QueryOperatorTypes.BIGGER, 26));
        groupCount.setAtLeast(1);
        Assertions.assertThrows(
                QueryRejectedException.class, () -> queryBuilderBO.buildCountDistinctSql(queryTree(groupCount), COHORT_IDS));
    }

    @Test
    @Order(34)
    void testNumericAndBooleanValuesAreValidatedAsLiterals() {
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.SMALLER, 28);
        String ageSql = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertTrue(ageSql.contains("agg_value < 28"));
        assertSqlExecutes(ageSql);
        Assertions.assertEquals(
                List.of(1L), matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L, 5L))));

        QueryItemDTO injectedAge = leaf("2002", "1002", QueryOperatorTypes.SMALLER, "28 OR pg_sleep(1)");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(injectedAge), COHORT_IDS));

        QueryItemDTO injectedList = leaf("2002", "1002", QueryOperatorTypes.IN, "25, 1 OR 1=1");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(injectedList), COHORT_IDS));

        QueryItemDTO injectedBoolean = leaf("2008", "1004", QueryOperatorTypes.EQUAL, "true OR pg_sleep(1)");
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(injectedBoolean), COHORT_IDS));

        QueryItemDTO booleanLeaf = leaf("2008", "1004", QueryOperatorTypes.EQUAL, "true");
        String booleanSql = queryBuilderBO.buildCountDistinctSql(queryTree(booleanLeaf), COHORT_IDS);
        Assertions.assertTrue(booleanSql.contains("agg_value = true"));
        Assertions.assertFalse(booleanSql.contains("pg_sleep"));
        assertSqlExecutes(booleanSql);

        QueryItemDTO firstInjected = leaf("2002", "1002", QueryOperatorTypes.BIGGER, "0; SELECT pg_sleep(1)");
        firstInjected.setTimeAggregationMethod(TimeAggregationMethod.FIRST);
        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(firstInjected), COHORT_IDS));
    }

    @Test
    @Order(35)
    void testInvalidRegexIsRejectedBeforeExecution() {
        QueryItemDTO invalid = leaf("2001", "1001", QueryOperatorTypes.REGEX, "(");
        QueryRejectedException rejected = Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(invalid), COHORT_IDS));
        Assertions.assertEquals("Regular expression is not valid", rejected.getMessage());

        QueryItemDTO valid = leaf("2001", "1001", QueryOperatorTypes.REGEX, "Diabetes");
        String sql = queryBuilderBO.buildCountDistinctSql(queryTree(valid), COHORT_IDS);
        Assertions.assertTrue(sql.contains("agg_value ~ 'Diabetes'"));
        assertSqlExecutes(sql);
    }

    @Test
    @Order(36)
    void testCountsAreLimitedToTheAllowedCohorts() {
        QueryItemDTO age = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 26);
        String unscoped = queryBuilderBO.buildCountDistinctSql(queryTree(age), COHORT_IDS);
        Assertions.assertFalse(queryAO.findPatientCountsByCohort(unscoped).isEmpty());
        Assertions.assertTrue(queryAO.findPatientCountsByCohort(unscoped, List.of(2L)).isEmpty());
        Assertions.assertTrue(queryAO.findPatientCountsByCohort(unscoped, List.of()).isEmpty());

        Assertions.assertEquals(
                List.of(1L, 2L),
                matchedPatientIds(queryBO.runQuery(queryTree(age), List.of(1L))));

        QueryItemDTO tooTall = leaf("2003", "1002", QueryOperatorTypes.BIGGER, 1000);
        QueryResultWrapperDTO none = queryBO.runQuery(queryTree(tooTall), List.of(2L));
        Assertions.assertTrue(none.getResult().isEmpty());

        Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBO.runQuery(queryTree(age), List.of(2L)));
    }

    @Test
    @Order(37)
    void testIntervalsBeyondTwoHundredYearsAreRejected() {
        QueryItemDTO temporal = and(leaf("2001", "1001", QueryOperatorTypes.EXISTS, null), leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0));
        temporal.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.WITHIN, interval("300000 years"))));
        assertIntervalTooLarge(temporal);

        QueryItemDTO following = and(leaf("2001", "1001", QueryOperatorTypes.EXISTS, null), leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0));
        following.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.IN_THE_FOLLOWING, interval("300000 years"))));
        assertIntervalTooLarge(following);

        QueryItemDTO withinCount = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        withinCount.setValueMode(ValueMode.DELTA);
        withinCount.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_LEAST);
        withinCount.setTimeWindow(interval("300000 years"));
        withinCount.setTimeWindowCount(1);
        assertIntervalTooLarge(withinCount);

        QueryItemDTO withinAverage = leaf("2002", "1002", QueryOperatorTypes.BIGGER_EQUAL, 30);
        withinAverage.setTimeAggregationMethod(TimeAggregationMethod.WITHIN);
        withinAverage.setTimeWindow(interval("300000 years"));
        withinAverage.setValueAggregationMethod(ValueAggregationMethod.AVERAGE);
        assertIntervalTooLarge(withinAverage);

        QueryItemDTO window = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        window.setValueMode(ValueMode.DELTA);
        window.setDeltaInterval(interval("300000 years"));
        assertIntervalTooLarge(window);

        QueryItemDTO summedParts = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        summedParts.setValueMode(ValueMode.DELTA);
        summedParts.setDeltaInterval(interval("150 years 60 years"));
        assertIntervalTooLarge(summedParts);

        QueryItemDTO intMaxWeeks = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        intMaxWeeks.setValueMode(ValueMode.DELTA);
        intMaxWeeks.setDeltaInterval(interval("2147483647 weeks"));
        assertIntervalTooLarge(intMaxWeeks);

        QueryItemDTO atLimit = and(leaf("2001", "1001", QueryOperatorTypes.EXISTS, null), leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0));
        atLimit.setTemporalRelations(List.of(relation(0, 1, TemporalRelationship.WITHIN, interval("200 years"))));
        assertSqlExecutes(queryBuilderBO.buildCountDistinctSql(queryTree(atLimit), COHORT_IDS));

        QueryItemDTO windowAtLimit = leaf("2002", "1002", QueryOperatorTypes.BIGGER, 0);
        windowAtLimit.setValueMode(ValueMode.DELTA);
        windowAtLimit.setDeltaInterval(interval("200 years"));
        assertSqlExecutes(queryBuilderBO.buildCountDistinctSql(queryTree(windowAtLimit), COHORT_IDS));
    }

    private void assertIntervalTooLarge(QueryItemDTO root) {
        QueryRejectedException rejected = Assertions.assertThrows(
                QueryRejectedException.class,
                () -> queryBuilderBO.buildCountDistinctSql(queryTree(root), COHORT_IDS));
        Assertions.assertTrue(
                rejected.getMessage().contains("exceeds the maximum of 200 years"),
                rejected.getMessage());
    }

    private QueryDTO queryTree(QueryItemDTO root) {
        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setQuery(root);
        return queryDTO;
    }

    private QueryItemDTO and(QueryItemDTO... conditions) {
        return group(ConnectingOperator.AND, conditions);
    }

    private QueryItemDTO or(QueryItemDTO... conditions) {
        return group(ConnectingOperator.OR, conditions);
    }

    private QueryItemDTO group(ConnectingOperator connectingOperator, QueryItemDTO... conditions) {
        QueryItemDTO group = new QueryItemDTO();
        group.setConnectingOperator(connectingOperator);
        group.setConditions(List.of(conditions));
        return group;
    }

    private QueryItemDTO leaf(String ontologyId, String dataTypeId, QueryOperatorTypes operator, Object value) {
        QueryItemDTO leaf = new QueryItemDTO();
        leaf.setOntologyId(ontologyId);
        leaf.setDataTypeId(dataTypeId);
        leaf.setOperator(operator);
        leaf.setValue(value == null ? null : String.valueOf(value));
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

    // "2 weeks 3 days" as interval parts
    private List<IntervalPartDTO> interval(String text) {
        List<IntervalPartDTO> parts = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d+)\\s+(\\w+?)s?\\b").matcher(text);
        while (matcher.find()) {
            IntervalUnit unit = IntervalUnit.valueOf(matcher.group(2).toUpperCase(Locale.ROOT) + "S");
            parts.add(new IntervalPartDTO(Integer.parseInt(matcher.group(1)), unit));
        }
        return parts;
    }

    private QueryDTO singleItemQuery(String ontologyId, String dataTypeId, QueryOperatorTypes operator, String value) {
        return queryTree(QueryItemHelper.andGroup(leaf(ontologyId, dataTypeId, operator, value)));
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
