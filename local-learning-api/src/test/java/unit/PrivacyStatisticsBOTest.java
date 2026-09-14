package unit;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.privacy.PrivacyStatisticsBO;
import bio.cosy.feddb.local.api.statistics.LocalDataStatisticsDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrivacyStatisticsBOTest {

    @Test
    void modifyStatisticsRoundsValuesAndRemovesSensitiveDetailsByDefault() {
        LocalDataStatisticsDTO statistics = new LocalDataStatisticsDTO();
        statistics.setPatientIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L));
        statistics.setCohortIds(List.of(10L));
        statistics.setProperties(List.of(
                new ColumnProfile(
                        "Age",
                        "NUMBER",
                        13L,
                        1L,
                        13,
                        42.26,
                        10.04,
                        18.0,
                        30.04,
                        42.25,
                        50.98,
                        91.0,
                        null
                ),
                new ColumnProfile(
                        "Status",
                        "TEXT",
                        12L,
                        0L,
                        4,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(Map.entry("A", 2), Map.entry("B", 3), Map.entry("C", 6))
                )
        ));

        LocalDataStatisticsDTO result = new PrivacyStatisticsBO().modifyStatistics(statistics);

        Assertions.assertEquals(List.of(), result.getPatientIds());
        Assertions.assertEquals(List.of(), result.getCohortIds());

        ColumnProfile age = result.getProperties().get(0);
        Assertions.assertEquals(15L, age.count());
        Assertions.assertEquals(5L, age.missing());
        Assertions.assertEquals(15, age.uniqueValues());
        Assertions.assertEquals(42.3, age.mean());
        Assertions.assertEquals(10.0, age.std());
        Assertions.assertNull(age.min());
        Assertions.assertEquals(30.0, age.p25());
        Assertions.assertEquals(42.3, age.median());
        Assertions.assertEquals(51.0, age.p75());
        Assertions.assertNull(age.max());

        ColumnProfile status = result.getProperties().get(1);
        Assertions.assertEquals(15L, status.count());
        Assertions.assertEquals(0L, status.missing());
        Assertions.assertEquals(5, status.uniqueValues());
        Assertions.assertEquals(2, status.topCategories().size());
        Assertions.assertEquals("B", status.topCategories().get(0).getKey());
        Assertions.assertEquals(5, status.topCategories().get(0).getValue());
        Assertions.assertEquals("C", status.topCategories().get(1).getKey());
        Assertions.assertEquals(10, status.topCategories().get(1).getValue());
    }

    @Test
    void modifyStatisticsSuppressesProfilesWhenPatientSetIsTooSmall() {
        LocalDataStatisticsDTO statistics = new LocalDataStatisticsDTO();
        statistics.setPatientIds(List.of(1L, 2L));
        statistics.setCohortIds(List.of(10L));
        statistics.setProperties(List.of(
                new ColumnProfile("Age", "NUMBER", 2L, 0L, 2, 42.0, 1.0, 41.0, 41.5, 42.0, 42.5, 43.0, null)
        ));

        LocalDataStatisticsDTO result = new PrivacyStatisticsBO().modifyStatistics(statistics);

        Assertions.assertEquals(List.of(), result.getProperties());
        Assertions.assertEquals(List.of(), result.getPatientIds());
        Assertions.assertEquals(List.of(), result.getCohortIds());
    }

    @Test
    void modifyStatisticsUsesConfigurableThresholdsAndRounding() throws Exception {
        PrivacyStatisticsBO bo = new PrivacyStatisticsBO();
        injectField(bo, "config", config(
                true,
                3,
                10,
                10,
                2,
                0,
                false,
                true
        ));

        LocalDataStatisticsDTO statistics = new LocalDataStatisticsDTO();
        statistics.setPatientIds(List.of(1L, 2L, 3L));
        statistics.setCohortIds(List.of(10L));
        statistics.setProperties(List.of(
                new ColumnProfile("Age", "NUMBER", 3L, 0L, 3, 42.6, 1.2, 40.2, 41.2, 42.6, 44.0, 45.4, null)
        ));

        LocalDataStatisticsDTO result = bo.modifyStatistics(statistics);
        ColumnProfile age = result.getProperties().getFirst();

        Assertions.assertEquals(List.of(1L, 2L, 3L), result.getPatientIds());
        Assertions.assertEquals(List.of(10L), result.getCohortIds());
        Assertions.assertEquals(10L, age.count());
        Assertions.assertEquals(10, age.uniqueValues());
        Assertions.assertEquals(43.0, age.mean());
        Assertions.assertEquals(40.0, age.min());
        Assertions.assertEquals(45.0, age.max());
    }

    private static FLNetClientConfig config(
            boolean enabled,
            int minSubjects,
            int countRoundingBase,
            int uniqueRoundingBase,
            int minCategoryCount,
            int numericDecimals,
            boolean removeExtremeValues,
            boolean keepLocalIds
    ) {
        FLNetClientConfig config = mock(FLNetClientConfig.class);
        FLNetClientConfig.PrivacyConfig privacyConfig = mock(FLNetClientConfig.PrivacyConfig.class);
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = mock(FLNetClientConfig.StatisticsPrivacyConfig.class);

        when(config.privacy()).thenReturn(privacyConfig);
        when(privacyConfig.statistics()).thenReturn(statisticsConfig);
        when(statisticsConfig.enabled()).thenReturn(enabled);
        when(statisticsConfig.minSubjects()).thenReturn(minSubjects);
        when(statisticsConfig.countRoundingBase()).thenReturn(countRoundingBase);
        when(statisticsConfig.uniqueRoundingBase()).thenReturn(uniqueRoundingBase);
        when(statisticsConfig.minCategoryCount()).thenReturn(minCategoryCount);
        when(statisticsConfig.numericDecimals()).thenReturn(numericDecimals);
        when(statisticsConfig.removeExtremeValues()).thenReturn(removeExtremeValues);
        when(statisticsConfig.keepLocalIds()).thenReturn(keepLocalIds);
        return config;
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
