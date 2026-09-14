package bio.cosy.feddb.local.api.statistics;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryHelper;

import java.util.*;

public final class PropertyAccumulator {
    private final Long schemaNodeId;
    private final String name;
    private final String type;
    private long count;
    private long missing;
    private final List<Double> numericValues = new ArrayList<>();
    private final Map<String, Integer> frequencies = new LinkedHashMap<>();

    public PropertyAccumulator(Long schemaNodeId, String name, String type) {
        this.schemaNodeId = schemaNodeId;
        this.name = name;
        this.type = type;
    }

    Long schemaNodeId() {
        return schemaNodeId;
    }

    String name() {
        return name;
    }

    void accept(PatientDataEntryEntity entry) {
        count++;

        Object value = PatientDataEntryHelper.getValue(entry);
        if (value == null) {
            missing++;
            return;
        }

        String frequencyKey = String.valueOf(value);
        frequencies.merge(frequencyKey, 1, Integer::sum);

        if (isNumericType(type)) {
            numericValues.add(((Number) value).doubleValue());
        }
    }

    ColumnProfile toProfile() {
        List<Double> sortedNumbers = new ArrayList<>(numericValues);
        sortedNumbers.sort(Double::compareTo);

        Double mean = null;
        Double std = null;
        Double min = null;
        Double p25 = null;
        Double median = null;
        Double p75 = null;
        Double max = null;

        if (!sortedNumbers.isEmpty()) {
            min = sortedNumbers.getFirst();
            max = sortedNumbers.getLast();
            mean = sortedNumbers.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            std = calculateStandardDeviation(sortedNumbers, mean);
            p25 = percentile(sortedNumbers, 0.25);
            median = percentile(sortedNumbers, 0.50);
            p75 = percentile(sortedNumbers, 0.75);
        }

        List<Map.Entry<String, Integer>> topCategories = null;
        if (!isNumericType(type) && !frequencies.isEmpty()) {
            topCategories = frequencies.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(10)
                    .toList();
        }

        return new ColumnProfile(
                name,
                type,
                count,
                missing,
                frequencies.size(),
                mean,
                std,
                min,
                p25,
                median,
                p75,
                max,
                topCategories,
                null
        );
    }

    private static boolean isNumericType(String type) {
        return "INTEGER".equals(type) || "NUMBER".equals(type);
    }

    private static Double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) {
            return null;
        }
        double variance = values.stream()
                .mapToDouble(value -> {
                    double diff = value - mean;
                    return diff * diff;
                })
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static Double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.getFirst();
        }

        double position = percentile * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        double lower = sortedValues.get(lowerIndex);
        double upper = sortedValues.get(upperIndex);
        double fraction = position - lowerIndex;
        return lower + (upper - lower) * fraction;
    }
}
