package bio.cosy.feddb.local.api.privacy;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.statistics.LocalDataStatisticsDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PrivacyStatisticsBO {

    private static final int DEFAULT_MIN_SUBJECTS = 10;
    private static final int DEFAULT_COUNT_ROUNDING_BASE = 5;
    private static final int DEFAULT_UNIQUE_ROUNDING_BASE = 5;
    private static final int DEFAULT_MIN_CATEGORY_COUNT = 3;
    private static final int DEFAULT_NUMERIC_DECIMALS = 1;

    @Inject
    FLNetClientConfig config;

    public LocalDataStatisticsDTO modifyStatistics(LocalDataStatisticsDTO statistics) {
        if (statistics == null || !privacyEnabled()) {
            return statistics;
        }

        LocalDataStatisticsDTO modified = new LocalDataStatisticsDTO();
        int patientCount = statistics.getPatientIds() == null ? 0 : statistics.getPatientIds().size();
        if (patientCount > 0 && patientCount < minSubjects()) {
            modified.setProperties(Collections.emptyList());
        } else {
            modified.setProperties(sanitizeProfiles(statistics.getProperties()));
        }

        if (keepLocalIds()) {
            modified.setCohortIds(statistics.getCohortIds());
            modified.setPatientIds(statistics.getPatientIds());
        } else {
            modified.setCohortIds(Collections.emptyList());
            modified.setPatientIds(Collections.emptyList());
        }
        return modified;
    }

    private List<ColumnProfile> sanitizeProfiles(List<ColumnProfile> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }
        return properties.stream()
                .map(this::sanitizeProfile)
                .toList();
    }

    private ColumnProfile sanitizeProfile(ColumnProfile profile) {
        long count = roundUp(profile.count(), countRoundingBase());
        long missing = roundUp(profile.missing(), countRoundingBase());
        int uniqueValues = roundUp(profile.uniqueValues(), uniqueRoundingBase());

        if (nonMissingCount(profile) < minSubjects()) {
            return new ColumnProfile(
                    profile.name(),
                    profile.type(),
                    count,
                    missing,
                    uniqueValues,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new ColumnProfile(
                profile.name(),
                profile.type(),
                count,
                missing,
                uniqueValues,
                round(profile.mean()),
                round(profile.std()),
                removeExtremeValues() ? null : round(profile.min()),
                round(profile.p25()),
                round(profile.median()),
                round(profile.p75()),
                removeExtremeValues() ? null : round(profile.max()),
                sanitizeTopCategories(profile.topCategories()),
                null
        );
    }

    private List<Map.Entry<String, Integer>> sanitizeTopCategories(List<Map.Entry<String, Integer>> topCategories) {
        if (topCategories == null || topCategories.isEmpty()) {
            return null;
        }

        List<Map.Entry<String, Integer>> sanitized = topCategories.stream()
                .filter(entry -> entry != null && entry.getValue() != null)
                .filter(entry -> entry.getValue() >= minCategoryCount())
                .map(entry -> (Map.Entry<String, Integer>) new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        roundUp(entry.getValue(), countRoundingBase())
                ))
                .toList();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private long nonMissingCount(ColumnProfile profile) {
        return Math.max(0, profile.count() - profile.missing());
    }

    private Double round(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }

        return BigDecimal.valueOf(value)
                .setScale(numericDecimals(), RoundingMode.HALF_UP)
                .doubleValue();
    }

    private long roundUp(long value, int base) {
        if (value <= 0 || base <= 1) {
            return value;
        }
        return ((value + base - 1) / base) * (long) base;
    }

    private int roundUp(int value, int base) {
        return Math.toIntExact(roundUp((long) value, base));
    }

    private boolean privacyEnabled() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return statisticsConfig == null || statisticsConfig.enabled();
    }

    private int minSubjects() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return Math.max(0, statisticsConfig == null ? DEFAULT_MIN_SUBJECTS : statisticsConfig.minSubjects());
    }

    private int countRoundingBase() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return Math.max(1, statisticsConfig == null ? DEFAULT_COUNT_ROUNDING_BASE : statisticsConfig.countRoundingBase());
    }

    private int uniqueRoundingBase() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return Math.max(1, statisticsConfig == null ? DEFAULT_UNIQUE_ROUNDING_BASE : statisticsConfig.uniqueRoundingBase());
    }

    private int minCategoryCount() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return Math.max(0, statisticsConfig == null ? DEFAULT_MIN_CATEGORY_COUNT : statisticsConfig.minCategoryCount());
    }

    private int numericDecimals() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return Math.max(0, statisticsConfig == null ? DEFAULT_NUMERIC_DECIMALS : statisticsConfig.numericDecimals());
    }

    private boolean removeExtremeValues() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return statisticsConfig == null || statisticsConfig.removeExtremeValues();
    }

    private boolean keepLocalIds() {
        FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig = statisticsConfig();
        return statisticsConfig != null && statisticsConfig.keepLocalIds();
    }

    private FLNetClientConfig.StatisticsPrivacyConfig statisticsConfig() {
        if (config == null || config.privacy() == null) {
            return null;
        }
        return config.privacy().statistics();
    }
}
