package bio.cosy.feddb.core.api.file.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.statistics.descriptive.DoubleStatistics;
import org.apache.commons.statistics.descriptive.Quantile;
import org.apache.commons.statistics.descriptive.Statistic;
import org.apache.datasketches.cpc.CpcSketch;
import org.apache.datasketches.frequencies.ErrorType;
import org.apache.datasketches.frequencies.FrequentItemsSketch;
import org.apache.datasketches.kll.KllDoublesSketch;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates column statistics one value at a time.
 *
 * <p>Profiling used to be a second pass: the reader wrote every row to a spill file and the profiler
 * read it back, decoding each record into a map only to hand the same values to these aggregators.
 * Exposing the accumulator lets a reader feed values while it already has them, so a large file is
 * walked once instead of twice.</p>
 *
 * <p>Not thread-safe; one instance belongs to one table being read by one thread.</p>
 */
public final class RowProfiler {

    /** Number of rendered rows kept as a sample of the input. */
    private static final int MAX_SAMPLES = 3;

    private final List<String> columns;
    private final ColAgg[] aggregators;
    private final List<String> samples = new ArrayList<>(MAX_SAMPLES);

    /** Shards report nothing: their coordinator owns the row count and speaks for the whole table. */
    private final RowProgress progress;

    private long rowCount;
    private LinkedHashMap<String, String> pendingSample;

    private RowProfiler(List<String> columns, RowProgress progress) {
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.aggregators = new ColAgg[this.columns.size()];
        for (int index = 0; index < this.columns.size(); index++) {
            aggregators[index] = new ColAgg(this.columns.get(index));
        }
        this.progress = progress;
    }

    public static RowProfiler of(List<String> columns) {
        return of(columns, null);
    }

    /**
     * @param source what is being profiled, for the progress log
     */
    public static RowProfiler of(List<String> columns, String source) {
        return of(columns, source, RowScanListener.NONE);
    }

    /**
     * @param listener told how the scan is coming along, for whoever is watching the import
     */
    public static RowProfiler of(List<String> columns, String source, RowScanListener listener) {
        String detail = columns == null ? "" : ", " + columns.size() + " columns";
        return new RowProfiler(columns, new RowProgress("Statistics", source, detail, listener));
    }

    /**
     * A profiler for part of a table, fed by {@link ShardedRowProfiler}. It keeps no samples and
     * reports no progress, both of which belong to whoever is coordinating the shards.
     */
    static RowProfiler shard(List<String> columns) {
        return new RowProfiler(columns, null);
    }

    public List<String> columns() {
        return columns;
    }

    public long rowCount() {
        return rowCount;
    }

    /** Feeds one raw cell. {@code columnIndex} outside the declared columns is ignored. */
    public void accept(int columnIndex, String value) {
        if (columnIndex < 0 || columnIndex >= aggregators.length) {
            return;
        }
        aggregators[columnIndex].accept(value);
        if (pendingSample != null) {
            pendingSample.put(columns.get(columnIndex), value);
        }
    }

    /** Feeds one already-typed cell, skipping the round trip through {@code String} where possible. */
    public void acceptObject(int columnIndex, Object value) {
        if (columnIndex < 0 || columnIndex >= aggregators.length) {
            return;
        }
        aggregators[columnIndex].acceptObject(value);
        if (pendingSample != null) {
            pendingSample.put(columns.get(columnIndex), value == null ? null : String.valueOf(value));
        }
    }

    public void acceptJson(int columnIndex, JsonNode value) {
        if (columnIndex < 0 || columnIndex >= aggregators.length) {
            return;
        }
        aggregators[columnIndex].acceptJson(value);
        if (pendingSample != null) {
            pendingSample.put(columns.get(columnIndex), jsonValueAsString(value));
        }
    }

    /** Convenience for callers that already hold the row as a map. */
    public void acceptRow(Map<String, Object> row) {
        if (samples.size() < MAX_SAMPLES) {
            samples.add(String.valueOf(row));
        }
        for (int index = 0; index < aggregators.length; index++) {
            aggregators[index].acceptObject(row == null ? null : row.get(columns.get(index)));
        }
        completeRow();
    }

    /** Opens a row for the indexed {@code accept} calls; pairs with {@link #endRow()}. */
    public void startRow() {
        if (progress != null && samples.size() < MAX_SAMPLES) {
            pendingSample = new LinkedHashMap<>();
        }
    }

    /**
     * Feeds a whole row of cells in column order. A row shorter than the column list counts as
     * missing for the columns it does not reach, which is how a ragged row is read everywhere else.
     */
    public void accept(Object[] values) {
        startRow();
        for (int index = 0; index < aggregators.length; index++) {
            acceptObject(index, values != null && index < values.length ? values[index] : null);
        }
        endRow();
    }

    /** Closes the row opened by {@link #startRow()}. */
    public void endRow() {
        if (pendingSample != null) {
            samples.add(pendingSample.toString());
            pendingSample = null;
        }
        completeRow();
    }

    private void completeRow() {
        rowCount++;
        if (progress != null) {
            progress.rowDone(rowCount);
        }
    }

    /** The finished profiles, for a coordinator reassembling a table from its shards. */
    List<ColumnProfile> profiles(long totalRows) {
        List<ColumnProfile> profiles = new ArrayList<>(aggregators.length);
        for (ColAgg aggregator : aggregators) {
            profiles.add(aggregator.toProfile(totalRows));
        }
        return profiles;
    }

    /** Builds the profiles. The instance must not be fed any further afterwards. */
    public FileProfile finish() {
        List<ColumnProfile> profiles = new ArrayList<>(aggregators.length);
        for (ColAgg aggregator : aggregators) {
            profiles.add(aggregator.toProfile(rowCount));
        }
        return new FileProfile("", rowCount, profiles, List.copyOf(samples));
    }

    // ---------- Value rendering ----------

    static String jsonValueAsString(JsonNode v) {
        if (v == null || v.isNull()) return null;
        if (v.isTextual()) return v.asText();
        if (v.isNumber()) return v.numberValue().toString();
        if (v.isBoolean()) return String.valueOf(v.asBoolean());
        // arrays/objects -> compact JSON
        return v.toString();
    }

    // ---------- Missing/Parsing Helpers ----------

    private static final Set<String> MISSING_TOKENS = Set.of("", "na", "n/a", "nan", "null", "none");

    /** Longest token in {@link #MISSING_TOKENS}; anything longer skips the lowercase/lookup work. */
    private static final int MAX_MISSING_TOKEN_LENGTH = 4;

    private static boolean isMissing(String s) {
        if (s == null) return true;
        String v = s.trim();
        if (v.length() > MAX_MISSING_TOKEN_LENGTH) return false;
        return MISSING_TOKENS.contains(v.toLowerCase(Locale.ROOT));
    }

    private static boolean isBooleanToken(String s) {
        if (s == null) return false;
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("1") || v.equals("0") || v.equals("yes") || v.equals("no");
    }

    private static boolean parseBooleanToken(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }

    private static Double parseDoubleSmart(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;

        // The grammar check keeps text values off the exception path: a rejected
        // Double.parseDouble() fills in a stack trace, which dominated profiling
        // time on text columns with millions of rows.
        if (isDecimalNumber(v, '.')) {
            return Double.parseDouble(v);
        }
        // EU comma as decimal separator (only if no dot is present)
        if (v.indexOf(',') >= 0 && v.indexOf('.') < 0 && isDecimalNumber(v, ',')) {
            return Double.parseDouble(v.replace(',', '.'));
        }
        return parseNonFiniteLiteral(v);
    }

    /**
     * Validates the decimal grammar {@code [+-]?(digits[sep digits?]|sep digits)([eE][+-]?digits)?[fFdD]?}
     * accepted by {@link Double#parseDouble(String)}, with {@code separator} as the decimal mark.
     *
     * <p>Hexadecimal float literals are deliberately not recognised; they do not occur in tabular
     * source data and would otherwise cost every text cell an exception.</p>
     */
    private static boolean isDecimalNumber(String v, char separator) {
        final int n = v.length();
        int i = 0;
        char c = v.charAt(i);
        if (c == '+' || c == '-') i++;

        int digits = 0;
        while (i < n && isAsciiDigit(v.charAt(i))) {
            i++;
            digits++;
        }
        if (i < n && v.charAt(i) == separator) {
            i++;
            while (i < n && isAsciiDigit(v.charAt(i))) {
                i++;
                digits++;
            }
        }
        if (digits == 0) return false;

        if (i < n && (v.charAt(i) == 'e' || v.charAt(i) == 'E')) {
            i++;
            if (i < n && (v.charAt(i) == '+' || v.charAt(i) == '-')) i++;
            int exponentDigits = 0;
            while (i < n && isAsciiDigit(v.charAt(i))) {
                i++;
                exponentDigits++;
            }
            if (exponentDigits == 0) return false;
        }
        if (i < n) {
            char suffix = v.charAt(i);
            if (suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D') i++;
        }
        return i == n;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** {@code NaN} / {@code Infinity} are valid doubles but not covered by {@link #isDecimalNumber}. */
    private static Double parseNonFiniteLiteral(String v) {
        char c0 = v.charAt(0);
        boolean signed = c0 == '+' || c0 == '-';
        String body = signed ? v.substring(1) : v;
        if (body.equals("NaN")) return Double.NaN;
        if (body.equals("Infinity")) return c0 == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        return null;
    }

    private static final DateTimeFormatter[] DATE_TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT
    };

    /**
     * Cheap shape check so that ordinary free text never reaches the parsers.
     * ISO-8601 tokens always start with a digit and contain a date or time separator.
     */
    private static boolean looksLikeDateTimeShape(String v) {
        char c0 = v.charAt(0);
        if (c0 < '0' || c0 > '9') return false;
        if (v.length() < 5) return false;
        boolean separator = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') continue;
            switch (c) {
                case '-', ':' -> separator = true;
                case '.', '+', 'T', 'Z', 't', 'z', ' ' -> { /* allowed */ }
                // A zone region suffix such as [Europe/Berlin] may hold arbitrary
                // text; leave the rest of the token to the parsers.
                case '[' -> {
                    return separator;
                }
                default -> {
                    return false;
                }
            }
        }
        return separator;
    }

    private static boolean isDateTimeToken(String s) {
        if (s == null) return false;
        String v = s.trim();
        if (v.isEmpty()) return false;
        if (!looksLikeDateTimeShape(v)) return false;
        // Ordinary ISO timestamps are settled here without going near a DateTimeFormatter. The
        // formatter path below costs two parses of every value - one to test the syntax, one to
        // resolve it - which on a timestamp column was an order of magnitude more per value than
        // any other column type and dominated the whole profiling phase.
        if (isIsoDateOrDateTime(v)) return true;
        for (DateTimeFormatter f : DATE_TIME_FORMATS) {
            try {
                // parseUnresolved() reports the common miss via ParsePosition rather than
                // by throwing, which keeps ordinary text off the exception path.
                ParsePosition pos = new ParsePosition(0);
                if (f.parseUnresolved(v, pos) == null || pos.getIndex() != v.length()) {
                    continue;
                }
                if (f == DateTimeFormatter.ISO_LOCAL_DATE) {
                    LocalDate.parse(v, f);
                } else if (f == DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
                    LocalDateTime.parse(v, f);
                } else if (f == DateTimeFormatter.ISO_LOCAL_TIME) {
                    LocalTime.parse(v, f);
                } else if (f == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    OffsetDateTime.parse(v, f);
                } else if (f == DateTimeFormatter.ISO_ZONED_DATE_TIME) {
                    ZonedDateTime.parse(v, f);
                } else {
                    DateTimeFormatter.ISO_INSTANT.parse(v);
                }
                return true;
            } catch (Exception ignore) {
            }
        }
        return false;
    }

    /**
     * Recognises the ISO forms that make up practically every timestamp column, with the same
     * strictness the formatters apply: {@code yyyy-MM-dd}, optionally {@code THH:mm[:ss[.f…]]}, and
     * optionally a {@code Z} or {@code ±HH:mm[:ss]} offset.
     *
     * <p>Deliberately conservative. A {@code false} here only sends the value on to the formatters,
     * so anything this does not recognise still gets classified exactly as it was before; a
     * {@code true} has to be a value the formatters would also have accepted, which is why the
     * calendar bounds are checked rather than just the digit layout.</p>
     */
    private static boolean isIsoDateOrDateTime(String v) {
        int length = v.length();
        if (length < 10 || !isDigits(v, 0, 4) || v.charAt(4) != '-' || v.charAt(7) != '-'
                || !isDigits(v, 5, 2) || !isDigits(v, 8, 2)) {
            return false;
        }

        int year = number(v, 0, 4);
        int month = number(v, 5, 2);
        int day = number(v, 8, 2);
        if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) {
            return false;
        }
        if (length == 10) {
            return true;
        }

        if (v.charAt(10) != 'T' || length < 16) {
            return false;
        }
        if (!isDigits(v, 11, 2) || v.charAt(13) != ':' || !isDigits(v, 14, 2)) {
            return false;
        }
        int hour = number(v, 11, 2);
        int minute = number(v, 14, 2);
        // 24:00 is valid ISO but resolves to the next day, which the formatters handle and this
        // does not, so it is left to them.
        if (hour > 23 || minute > 59) {
            return false;
        }

        int index = 16;
        if (index < length && v.charAt(index) == ':') {
            if (!isDigits(v, index + 1, 2)) {
                return false;
            }
            int second = number(v, index + 1, 2);
            if (second > 59) {
                return false;
            }
            index += 3;

            if (index < length && v.charAt(index) == '.') {
                int digits = 0;
                index++;
                while (index < length && isAsciiDigit(v.charAt(index)) && digits < 9) {
                    index++;
                    digits++;
                }
                if (digits == 0) {
                    return false;
                }
            }
        }

        return index == length || isIsoOffset(v, index);
    }

    private static boolean isIsoOffset(String v, int index) {
        int length = v.length();
        if (v.charAt(index) == 'Z') {
            return index + 1 == length;
        }
        char sign = v.charAt(index);
        if (sign != '+' && sign != '-') {
            return false;
        }
        if (index + 6 > length || !isDigits(v, index + 1, 2) || v.charAt(index + 3) != ':'
                || !isDigits(v, index + 4, 2)) {
            return false;
        }
        if (number(v, index + 1, 2) > 18 || number(v, index + 4, 2) > 59) {
            return false;
        }
        int end = index + 6;
        if (end == length) {
            return true;
        }
        // Optional seconds on the offset.
        return v.charAt(end) == ':' && end + 3 == length && isDigits(v, end + 1, 2)
                && number(v, end + 1, 2) <= 59;
    }

    private static int daysInMonth(int year, int month) {
        return switch (month) {
            case 2 -> isLeapYear(year) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    private static boolean isLeapYear(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    private static boolean isDigits(String v, int from, int count) {
        if (from + count > v.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            if (!isAsciiDigit(v.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int number(String v, int from, int count) {
        int value = 0;
        for (int i = from; i < from + count; i++) {
            value = value * 10 + (v.charAt(i) - '0');
        }
        return value;
    }

    private static Double safe(Double v) {
        return v == null || !Double.isFinite(v) ? null : v;
    }

    // ---------- Aggregator ----------

    /**
     * Per-column aggregator with bounded memory.
     *
     * <p>Distinct values, value frequencies and numeric values are tracked exactly while a column
     * stays below {@link #MAX_EXACT_VALUES}, so previews and ordinary categorical columns keep
     * byte-identical profiles. Once a column exceeds that budget the exact collections are released
     * and the profile is served from DataSketches summaries instead, which use a fixed amount of
     * memory regardless of input size. Without this, profiling a large file materialised the whole
     * dataset back onto the heap even though the rows were streamed from disk.</p>
     */
    private static final class ColAgg {

        /**
         * Per-column budget for exact tracking. Columns above this are almost never value-mapping
         * candidates (free-text, identifiers, timestamps), so degrading them to sketch estimates
         * costs nothing in practice. Tunable via {@code -Dfeddb.analytics.max-exact-values=...}.
         */
        private static final int MAX_EXACT_VALUES =
                Integer.getInteger("feddb.analytics.max-exact-values", 50_000);

        /** Frequent-items map size; must be a power of two. */
        private static final int TOP_ITEMS_MAP_SIZE = 4096;

        /** log2 of the distinct-count sketch size; 12 keeps the error near 1.6% for a few KB. */
        private static final int DISTINCT_SKETCH_LG_K = 12;

        final String name;

        long missing = 0;
        long nonMissing = 0;

        long numericCount = 0;
        long booleanCount = 0;
        long datetimeCount = 0;
        long intishCount = 0;

        int trueCount = 0;
        int falseCount = 0;

        /** Exact tracking, released once the column exceeds {@link #MAX_EXACT_VALUES}. */
        Map<String, Integer> freq = new HashMap<>();
        Set<String> unique = new HashSet<>();
        List<Double> numValues = new ArrayList<>();
        boolean exactValues = true;
        /**
         * Tracked separately from {@link #exactValues}: the numeric buffer grows per row, not per
         * distinct value, so a low-cardinality numeric column would otherwise keep one boxed double
         * for every row in the file.
         */
        boolean exactNumValues = true;

        final CpcSketch distinctSketch = new CpcSketch(DISTINCT_SKETCH_LG_K);
        /**
         * Only fed once exact tracking is dropped; until then {@link #freq} is authoritative and is
         * replayed into the sketch at that point. Feeding both would have cost a sketch update per
         * row for columns that never need the sketch at all.
         */
        FrequentItemsSketch<String> topItems;
        final KllDoublesSketch quantileSketch = KllDoublesSketch.newHeapInstance();

        final DoubleStatistics numStats = DoubleStatistics.builder(
                Statistic.MIN, Statistic.MAX, Statistic.MEAN, Statistic.VARIANCE).build();

        ColAgg(String name) {
            this.name = name;
        }

        /** Records a present value in both the exact collections (while affordable) and the sketches. */
        private void trackValue(String raw) {
            if (!exactValues) {
                distinctSketch.update(raw);
                topItems.update(raw);
                return;
            }

            // A distinct-count sketch only needs to see each value once, and the exact set already
            // knows whether this one is new, so repeats never reach it while the budget holds.
            if (unique.add(raw)) {
                distinctSketch.update(raw);
            }
            freq.merge(raw, 1, Integer::sum);
            if (unique.size() > MAX_EXACT_VALUES) {
                dropExactTracking();
            }
        }

        private void trackNumeric(double d) {
            numericCount++;
            numStats.accept(d);
            quantileSketch.update(d);
            if (Math.abs(d - Math.rint(d)) < 1e-9) {
                intishCount++;
            }
            if (exactNumValues) {
                numValues.add(d);
                if (numValues.size() > MAX_EXACT_VALUES) {
                    dropExactNumericValues();
                }
            }
        }

        private void dropExactTracking() {
            // Hand the exact distribution to the sketch in one weighted pass, so the bounded profile
            // starts from everything seen so far rather than only from what arrives next.
            topItems = new FrequentItemsSketch<>(TOP_ITEMS_MAP_SIZE);
            for (Map.Entry<String, Integer> entry : freq.entrySet()) {
                topItems.update(entry.getKey(), entry.getValue());
            }
            exactValues = false;
            unique = null;
            freq = null;
            dropExactNumericValues();
        }

        private void dropExactNumericValues() {
            exactNumValues = false;
            numValues = null;
        }

        void accept(String raw) {
            if (isMissing(raw)) {
                missing++;
                return;
            }
            nonMissing++;

            trackValue(raw);

            Double d = parseDoubleSmart(raw);
            if (d != null) {
                trackNumeric(d);
                return;
            }
            if (isBooleanToken(raw)) {
                booleanCount++;
                if (parseBooleanToken(raw)) trueCount++;
                else falseCount++;
                return;
            }
            if (isDateTimeToken(raw)) {
                datetimeCount++;
            }
        }

        void acceptJson(JsonNode node) {
            if (node == null || node.isNull()) {
                missing++;
                return;
            }

            // Treat empty string as missing to match CSV behavior
            if (node.isTextual() && isMissing(node.asText())) {
                missing++;
                return;
            }

            nonMissing++;

            // Use a stable string representation for uniqueness/frequencies
            String raw = jsonValueAsString(node);
            trackValue(raw);

            if (node.isNumber()) {
                trackNumeric(node.doubleValue());
                return;
            }

            if (node.isBoolean()) {
                booleanCount++;
                if (node.asBoolean()) trueCount++;
                else falseCount++;
                return;
            }

            if (node.isTextual() && isDateTimeToken(node.asText())) {
                datetimeCount++;
            }
        }

        void acceptObject(Object value) {
            if (value instanceof JsonNode jsonNode) {
                acceptJson(jsonNode);
                return;
            }
            if (value instanceof Number number) {
                // Already typed: skip the round trip through String parsing.
                String raw = String.valueOf(number);
                if (isMissing(raw)) {
                    missing++;
                    return;
                }
                nonMissing++;
                trackValue(raw);
                trackNumeric(number.doubleValue());
                return;
            }
            accept(value == null ? null : String.valueOf(value));
        }

        private boolean looksInteger() {
            if (numericCount == 0) return false;
            return intishCount >= Math.max(1, Math.round(numericCount * 0.98));
        }

        private int uniqueValues() {
            if (exactValues) {
                return unique.size();
            }
            double estimate = distinctSketch.getEstimate();
            return (int) Math.min(Integer.MAX_VALUE, Math.round(estimate));
        }

        /**
         * Exact, fully sorted frequencies while the column fits the budget; otherwise the sketch's
         * frequent items, which is a bounded top-k rather than the complete distribution.
         */
        private List<Map.Entry<String, Integer>> valueCounts() {
            if (exactValues) {
                return freq.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                                .thenComparing(Map.Entry.comparingByKey()))
                        .map(entry -> (Map.Entry<String, Integer>) new AbstractMap.SimpleImmutableEntry<>(
                                entry.getKey(), entry.getValue()))
                        .toList();
            }

            return Arrays.stream(topItems.getFrequentItems(ErrorType.NO_FALSE_POSITIVES))
                    .map(row -> (Map.Entry<String, Integer>) new AbstractMap.SimpleImmutableEntry<>(
                            row.getItem(),
                            (int) Math.min(Integer.MAX_VALUE, row.getEstimate())))
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .toList();
        }

        ColumnProfile toProfile(long totalRows) {
            String type;
            if (nonMissing > 0 && numericCount >= 0.8 * nonMissing) {
                type = looksInteger() ? "INTEGER" : "NUMBER";
            } else if (nonMissing > 0 && booleanCount >= 0.9 * nonMissing) {
                type = "BOOLEAN";
            } else if (nonMissing > 0 && datetimeCount >= 0.8 * nonMissing) {
                type = "DATETIME";
            } else {
                type = "TEXT";
            }

            Double mean = null, std = null, min = null, p25 = null, median = null, p75 = null, max = null;
            List<Map.Entry<String, Integer>> top = null;
            List<Map.Entry<String, Integer>> valueCounts = valueCounts();

            if (type.equals("NUMBER") || type.equals("INTEGER")) {
                min = safe(numStats.getAsDouble(Statistic.MIN));
                max = safe(numStats.getAsDouble(Statistic.MAX));
                mean = safe(numStats.getAsDouble(Statistic.MEAN));
                Double variance = safe(numStats.getAsDouble(Statistic.VARIANCE));
                std = (variance == null) ? null : safe(Math.sqrt(variance));

                if (exactNumValues && !numValues.isEmpty()) {
                    double[] arr = numValues.stream().mapToDouble(Double::doubleValue).toArray();
                    double[] qs = Quantile.withDefaults().evaluate(arr, 0.25, 0.50, 0.75);
                    p25 = safe(qs[0]);
                    median = safe(qs[1]);
                    p75 = safe(qs[2]);
                } else if (!quantileSketch.isEmpty()) {
                    p25 = safe(quantileSketch.getQuantile(0.25));
                    median = safe(quantileSketch.getQuantile(0.50));
                    p75 = safe(quantileSketch.getQuantile(0.75));
                }
            }

            if (!valueCounts.isEmpty() && !type.equals("NUMBER") && !type.equals("INTEGER")) {
                top = valueCounts.stream().limit(10).toList();
            }

            return new ColumnProfile(
                    name,
                    type,
                    totalRows,
                    missing,
                    uniqueValues(),
                    mean, std, min, p25, median, p75, max,
                    top,
                    valueCounts
            );
        }
    }
}
