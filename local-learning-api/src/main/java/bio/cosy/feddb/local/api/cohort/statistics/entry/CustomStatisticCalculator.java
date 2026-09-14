package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryHelper;
import bio.cosy.feddb.local.api.cohort.statistics.entry.config.*;
import bio.cosy.feddb.local.api.cohort.statistics.entry.data.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;

@ApplicationScoped
public class CustomStatisticCalculator {

    @Inject
    CohortAO cohortAO;

    public CustomStatisticData calc(CustomStatisticConfig config, Long cohortId) {
        List<PatientRow> rows = loadRows(cohortId);
        return switch (config) {
            case BarChartConfig c -> calcBar(c, rows);
            case PieChartConfig c -> calcPie(c, rows);
            case LineChartConfig c -> calcLine(c, rows);
            case ScatterChartConfig c -> calcScatter(c, rows);
            case HistogramConfig c -> calcHistogram(c, rows);
            case BoxplotConfig c -> calcBoxplot(c, rows);
            case HeatmapConfig c -> calcHeatmap(c, rows);
        };
    }

    // ---- Bar -----------------------------------------------------------------

    private BarChartData calcBar(BarChartConfig config, List<PatientRow> rows) {
        if (config.xProperty() == null) {
            return new BarChartData(List.of(), List.of());
        }
        CustomStatisticAggregation agg = nonNull(config.aggregation(), CustomStatisticAggregation.COUNT);
        BarChartConfig.SortBy sortBy = nonNull(config.sortBy(), BarChartConfig.SortBy.VALUE_DESC);
        Integer limit = config.limit();

        // group rows: category -> (group -> list of y-values (or null for count))
        Map<String, Map<String, List<Double>>> buckets = new LinkedHashMap<>();
        for (PatientRow row : rows) {
            String category = toLabel(row.values().get(config.xProperty()));
            if (category == null) continue;
            String group = config.groupByProperty() == null
                    ? "value"
                    : nonNullLabel(toLabel(row.values().get(config.groupByProperty())));
            Double yValue = config.yProperty() == null
                    ? 1.0
                    : toNumber(row.values().get(config.yProperty()));
            buckets.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .computeIfAbsent(group, k -> new ArrayList<>())
                    .add(yValue);
        }

        // collect distinct group names (order of appearance)
        LinkedHashSet<String> groupNames = new LinkedHashSet<>();
        buckets.values().forEach(m -> groupNames.addAll(m.keySet()));

        // compute aggregated category totals (used for sorting/limit)
        List<Map.Entry<String, Double>> totals = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<Double>>> entry : buckets.entrySet()) {
            double total = entry.getValue().values().stream()
                    .flatMap(Collection::stream)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            totals.add(Map.entry(entry.getKey(), total));
        }
        totals.sort(sortComparator(sortBy));
        if (limit != null && limit > 0 && totals.size() > limit) {
            totals = totals.subList(0, limit);
        }

        List<String> categories = totals.stream().map(Map.Entry::getKey).toList();
        List<BarChartData.Series> series = new ArrayList<>();
        for (String group : groupNames) {
            List<Double> values = new ArrayList<>(categories.size());
            for (String category : categories) {
                List<Double> raw = buckets.getOrDefault(category, Map.of())
                        .getOrDefault(group, List.of());
                values.add(aggregate(raw, agg));
            }
            series.add(new BarChartData.Series(group, values));
        }
        return new BarChartData(categories, series);
    }

    private Comparator<Map.Entry<String, Double>> sortComparator(BarChartConfig.SortBy sortBy) {
        return switch (sortBy) {
            case VALUE_DESC -> Map.Entry.<String, Double>comparingByValue().reversed();
            case VALUE_ASC -> Map.Entry.comparingByValue();
            case LABEL -> Map.Entry.comparingByKey();
        };
    }

    // ---- Pie -----------------------------------------------------------------

    private PieChartData calcPie(PieChartConfig config, List<PatientRow> rows) {
        if (config.property() == null) return new PieChartData(List.of());
        CustomStatisticAggregation agg = nonNull(config.aggregation(), CustomStatisticAggregation.COUNT);

        Map<String, List<Double>> buckets = new LinkedHashMap<>();
        for (PatientRow row : rows) {
            String slice = toLabel(row.values().get(config.property()));
            if (slice == null) continue;
            Double value = config.valueProperty() == null
                    ? 1.0
                    : toNumber(row.values().get(config.valueProperty()));
            buckets.computeIfAbsent(slice, k -> new ArrayList<>()).add(value);
        }

        List<PieChartData.Slice> slices = buckets.entrySet().stream()
                .map(e -> new PieChartData.Slice(e.getKey(), nullSafe(aggregate(e.getValue(), agg))))
                .sorted((a, b) -> Double.compare(b.value(), a.value()))
                .toList();

        if (config.topN() != null && config.topN() > 0 && slices.size() > config.topN()) {
            List<PieChartData.Slice> kept = new ArrayList<>(slices.subList(0, config.topN()));
            double other = slices.subList(config.topN(), slices.size()).stream()
                    .mapToDouble(PieChartData.Slice::value).sum();
            if (other > 0) kept.add(new PieChartData.Slice("Other", other));
            slices = kept;
        }
        return new PieChartData(slices);
    }

    // ---- Line ----------------------------------------------------------------

    private LineChartData calcLine(LineChartConfig config, List<PatientRow> rows) {
        if (config.xProperty() == null || config.yProperty() == null) {
            return new LineChartData(List.of(), List.of());
        }
        CustomStatisticAggregation agg = nonNull(config.aggregation(), CustomStatisticAggregation.AVG);

        // group: bucketKey -> seriesName -> values
        Map<String, Map<String, List<Double>>> buckets = new TreeMap<>();
        for (PatientRow row : rows) {
            Object xRaw = row.values().get(config.xProperty());
            String bucketKey = bucketKey(xRaw, config.granularity());
            if (bucketKey == null) continue;
            Double y = toNumber(row.values().get(config.yProperty()));
            if (y == null && agg != CustomStatisticAggregation.COUNT) continue;
            String series = config.groupByProperty() == null
                    ? "value"
                    : nonNullLabel(toLabel(row.values().get(config.groupByProperty())));
            buckets.computeIfAbsent(bucketKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(series, k -> new ArrayList<>())
                    .add(y != null ? y : 1.0);
        }

        List<String> xValues = new ArrayList<>(buckets.keySet());
        LinkedHashSet<String> seriesNames = new LinkedHashSet<>();
        buckets.values().forEach(m -> seriesNames.addAll(m.keySet()));

        List<LineChartData.Series> seriesList = new ArrayList<>();
        for (String name : seriesNames) {
            List<Double> values = new ArrayList<>();
            for (String x : xValues) {
                values.add(aggregate(buckets.get(x).getOrDefault(name, List.of()), agg));
            }
            seriesList.add(new LineChartData.Series(name, values));
        }
        return new LineChartData(xValues, seriesList);
    }

    // ---- Scatter -------------------------------------------------------------

    private ScatterChartData calcScatter(ScatterChartConfig config, List<PatientRow> rows) {
        if (config.xProperty() == null || config.yProperty() == null) {
            return new ScatterChartData(List.of());
        }
        Map<String, List<List<Double>>> groups = new LinkedHashMap<>();
        for (PatientRow row : rows) {
            Double x = toNumber(row.values().get(config.xProperty()));
            Double y = toNumber(row.values().get(config.yProperty()));
            if (x == null || y == null) continue;
            List<Double> point = new ArrayList<>(3);
            point.add(x);
            point.add(y);
            if (config.sizeProperty() != null) {
                Double size = toNumber(row.values().get(config.sizeProperty()));
                point.add(size != null ? size : 0.0);
            }
            String group = config.groupByProperty() == null
                    ? "points"
                    : nonNullLabel(toLabel(row.values().get(config.groupByProperty())));
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(point);
        }
        return new ScatterChartData(groups.entrySet().stream()
                .map(e -> new ScatterChartData.Group(e.getKey(), e.getValue()))
                .toList());
    }

    // ---- Histogram -----------------------------------------------------------

    private HistogramData calcHistogram(HistogramConfig config, List<PatientRow> rows) {
        if (config.property() == null) return new HistogramData(List.of());
        int binCount = config.bins() == null || config.bins() <= 0 ? 20 : config.bins();
        List<Double> values = new ArrayList<>();
        for (PatientRow row : rows) {
            Double v = toNumber(row.values().get(config.property()));
            if (v != null) values.add(v);
        }
        if (values.isEmpty()) return new HistogramData(List.of());
        double min = config.min() != null ? config.min() : Collections.min(values);
        double max = config.max() != null ? config.max() : Collections.max(values);
        if (max == min) max = min + 1;
        double step = (max - min) / binCount;

        long[] counts = new long[binCount];
        for (Double v : values) {
            if (v < min || v > max) continue;
            int idx = Math.min(binCount - 1, (int) Math.floor((v - min) / step));
            counts[idx]++;
        }
        List<HistogramData.Bin> bins = new ArrayList<>(binCount);
        for (int i = 0; i < binCount; i++) {
            bins.add(new HistogramData.Bin(min + i * step, min + (i + 1) * step, counts[i]));
        }
        return new HistogramData(bins);
    }

    // ---- Boxplot -------------------------------------------------------------

    private BoxplotData calcBoxplot(BoxplotConfig config, List<PatientRow> rows) {
        if (config.property() == null) return new BoxplotData(List.of());
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        for (PatientRow row : rows) {
            Double v = toNumber(row.values().get(config.property()));
            if (v == null) continue;
            String group = config.groupByProperty() == null
                    ? config.property()
                    : nonNullLabel(toLabel(row.values().get(config.groupByProperty())));
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(v);
        }
        boolean showOutliers = Boolean.TRUE.equals(config.showOutliers());
        List<BoxplotData.Group> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : groups.entrySet()) {
            List<Double> sorted = new ArrayList<>(entry.getValue());
            Collections.sort(sorted);
            double p25 = percentile(sorted, 0.25);
            double p50 = percentile(sorted, 0.50);
            double p75 = percentile(sorted, 0.75);
            double iqr = p75 - p25;
            double whiskerLow = p25 - 1.5 * iqr;
            double whiskerHigh = p75 + 1.5 * iqr;
            double minIn = sorted.stream().filter(v -> v >= whiskerLow).findFirst().orElse(sorted.getFirst());
            double maxIn = sorted.reversed().stream().filter(v -> v <= whiskerHigh).findFirst().orElse(sorted.getLast());
            List<Double> outliers = showOutliers
                    ? sorted.stream().filter(v -> v < whiskerLow || v > whiskerHigh).toList()
                    : List.of();
            result.add(new BoxplotData.Group(entry.getKey(), minIn, p25, p50, p75, maxIn, outliers));
        }
        return new BoxplotData(result);
    }

    // ---- Heatmap -------------------------------------------------------------

    private HeatmapData calcHeatmap(HeatmapConfig config, List<PatientRow> rows) {
        if (config.xProperty() == null || config.yProperty() == null) {
            return new HeatmapData(List.of(), List.of(), List.of());
        }
        CustomStatisticAggregation agg = nonNull(config.aggregation(), CustomStatisticAggregation.COUNT);

        // axis values
        List<String> xLabels = axisLabels(rows, config.xProperty(), config.xBins());
        List<String> yLabels = axisLabels(rows, config.yProperty(), config.yBins());
        Map<String, Integer> xIndex = indexOf(xLabels);
        Map<String, Integer> yIndex = indexOf(yLabels);

        Map<Long, List<Double>> buckets = new LinkedHashMap<>();
        for (PatientRow row : rows) {
            String xLabel = axisLabel(row.values().get(config.xProperty()), config.xBins(), xLabels);
            String yLabel = axisLabel(row.values().get(config.yProperty()), config.yBins(), yLabels);
            if (xLabel == null || yLabel == null) continue;
            Integer xi = xIndex.get(xLabel);
            Integer yi = yIndex.get(yLabel);
            if (xi == null || yi == null) continue;
            Double v = config.valueProperty() == null
                    ? 1.0
                    : toNumber(row.values().get(config.valueProperty()));
            long key = ((long) xi << 32) | (yi & 0xffffffffL);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        List<HeatmapData.Cell> cells = new ArrayList<>(buckets.size());
        for (Map.Entry<Long, List<Double>> entry : buckets.entrySet()) {
            int xi = (int) (entry.getKey() >> 32);
            int yi = (int) (long) entry.getKey();
            cells.add(new HeatmapData.Cell(xi, yi, nullSafe(aggregate(entry.getValue(), agg))));
        }
        return new HeatmapData(xLabels, yLabels, cells);
    }

    private List<String> axisLabels(List<PatientRow> rows, String property, Integer bins) {
        // numeric + bins requested → range labels; otherwise distinct categorical labels
        if (bins != null && bins > 0) {
            List<Double> values = new ArrayList<>();
            for (PatientRow row : rows) {
                Double v = toNumber(row.values().get(property));
                if (v != null) values.add(v);
            }
            if (values.isEmpty()) return List.of();
            double min = Collections.min(values);
            double max = Collections.max(values);
            if (max == min) max = min + 1;
            double step = (max - min) / bins;
            List<String> labels = new ArrayList<>(bins);
            for (int i = 0; i < bins; i++) {
                labels.add(formatRange(min + i * step, min + (i + 1) * step));
            }
            return labels;
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (PatientRow row : rows) {
            String label = toLabel(row.values().get(property));
            if (label != null) labels.add(label);
        }
        return new ArrayList<>(labels);
    }

    private String axisLabel(Object value, Integer bins, List<String> labels) {
        if (bins != null && bins > 0) {
            Double v = toNumber(value);
            if (v == null || labels.isEmpty()) return null;
            // recover min/step from first label
            double[] firstRange = parseRange(labels.getFirst());
            double[] lastRange = parseRange(labels.getLast());
            double min = firstRange[0];
            double max = lastRange[1];
            if (max == min) max = min + 1;
            double step = (max - min) / labels.size();
            int idx = Math.min(labels.size() - 1, Math.max(0, (int) Math.floor((v - min) / step)));
            return labels.get(idx);
        }
        return toLabel(value);
    }

    private static double[] parseRange(String label) {
        String[] parts = label.split("…", 2);
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }

    private static String formatRange(double start, double end) {
        return formatNumber(start) + "…" + formatNumber(end);
    }

    private static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Map<String, Integer> indexOf(List<String> labels) {
        Map<String, Integer> map = new HashMap<>(labels.size() * 2);
        for (int i = 0; i < labels.size(); i++) map.put(labels.get(i), i);
        return map;
    }

    // ---- Shared helpers ------------------------------------------------------

    private List<PatientRow> loadRows(Long cohortId) {
        if (cohortId == null) return List.of();
        Optional<CohortEntity> cohort = cohortAO.findByIdOptional(cohortId);
        if (cohort.isEmpty()) return List.of();
        Set<PatientEntity> patients = cohort.get().getPatients();
        if (patients == null) return List.of();
        List<PatientRow> rows = new ArrayList<>(patients.size());
        for (PatientEntity patient : patients) {
            if (patient == null) continue;
            Map<String, Object> values = new HashMap<>();
            for (PatientDataEntryEntity entry : safe(patient.getDataEntries())) {
                if (entry == null || entry.getSchemaNode() == null) continue;
                String name = entry.getSchemaNode().getName();
                if (name == null || values.containsKey(name)) continue;
                Object value = PatientDataEntryHelper.getValue(entry);
                if (value != null) values.put(name, value);
            }
            rows.add(new PatientRow(patient.getId(), values));
        }
        return rows;
    }

    private static <T> Collection<T> safe(Collection<T> values) {
        return values == null ? List.of() : values;
    }

    private static Double aggregate(List<Double> values, CustomStatisticAggregation agg) {
        List<Double> nonNull = values.stream().filter(Objects::nonNull).toList();
        if (agg == CustomStatisticAggregation.COUNT) {
            return (double) nonNull.size();
        }
        if (nonNull.isEmpty()) return null;
        return switch (agg) {
            case SUM -> nonNull.stream().mapToDouble(Double::doubleValue).sum();
            case AVG -> nonNull.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case MIN -> Collections.min(nonNull);
            case MAX -> Collections.max(nonNull);
            case MEDIAN -> {
                List<Double> sorted = new ArrayList<>(nonNull);
                Collections.sort(sorted);
                yield percentile(sorted, 0.50);
            }
            case COUNT -> (double) nonNull.size();
        };
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.getFirst();
        double pos = p * (sorted.size() - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (lower == upper) return sorted.get(lower);
        double fraction = pos - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }

    private static Double toNumber(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof Boolean b) return b ? 1.0 : 0.0;
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String toLabel(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d.toString();
        if (value instanceof Instant i) return i.toString();
        return String.valueOf(value);
    }

    private static String nonNullLabel(String label) {
        return label == null ? "(none)" : label;
    }

    private static String bucketKey(Object value, CustomStatisticTimeGranularity granularity) {
        if (value == null) return null;
        if (granularity == null || !(value instanceof Instant || value instanceof LocalDate)) {
            return toLabel(value);
        }
        LocalDate date = value instanceof Instant i
                ? i.atZone(ZoneOffset.UTC).toLocalDate()
                : (LocalDate) value;
        return switch (granularity) {
            case DAY -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case WEEK -> date.getYear() + "-W" + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case QUARTER -> date.getYear() + "-Q" + (((date.getMonthValue() - 1) / 3) + 1);
            case YEAR -> Integer.toString(date.getYear());
        };
    }

    private static <T> T nonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static double nullSafe(Double value) {
        return value == null ? 0.0 : value;
    }

    private record PatientRow(Long id, Map<String, Object> values) {}
}
