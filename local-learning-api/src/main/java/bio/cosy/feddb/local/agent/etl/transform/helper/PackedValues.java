package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.core.api.file.analytics.RowProfiler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PackedValues {

    private static final List<String> SEPARATORS = List.of("|", ";", "-", "/", ",");
    private static final int MIN_PARTS = 3;
    private static final int MAX_PART_LENGTH = 12;
    private static final double MIN_SHARE = 0.8;
    private static final Pattern DATE = Pattern.compile("^(\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4})([ T].*)?$");
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private PackedValues() {
    }

    public static String note(Collection<String> values) {
        List<String> filled = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty() && !RowProfiler.isMissing(value))
                .distinct()
                .toList();
        // Dates and ids also join their parts by a separator
        if (filled.size() < 2 || share(filled, value -> DATE.matcher(value).matches() || UUID.matcher(value).matches()) > 1 - MIN_SHARE) {
            return "";
        }
        for (String separator : SEPARATORS) {
            Map<Integer, Long> partCounts = filled.stream()
                    .collect(Collectors.groupingBy(value -> parts(value, separator).size(), Collectors.counting()));
            Map.Entry<Integer, Long> common = partCounts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            int count = common.getKey();
            List<String> packed = filled.stream().filter(value -> parts(value, separator).size() == count).toList();
            if (count >= MIN_PARTS && common.getValue() >= MIN_SHARE * filled.size()
                    && packed.stream().allMatch(value -> shortParts(value, separator)) && !fixedWidth(packed, separator)) {
                return " Packed: each cell joins " + count + " values by '" + separator + "', e.g. '" + packed.getFirst()
                        + "'; the column can hold several schema nodes, Split Column splits it.";
            }
        }
        return "";
    }

    /** Ids such as '999-76-6866' or 'C3L-00006-01' keep the length of every part; packed codes do not. */
    private static boolean fixedWidth(List<String> values, String separator) {
        return values.stream()
                .map(value -> parts(value, separator).stream().map(String::length).toList())
                .distinct()
                .count() == 1;
    }

    private static boolean shortParts(String value, String separator) {
        return parts(value, separator).stream().allMatch(part -> !part.isBlank() && part.length() <= MAX_PART_LENGTH);
    }

    private static List<String> parts(String value, String separator) {
        return List.of(value.split(Pattern.quote(separator), -1));
    }

    private static double share(List<String> values, Function<String, Boolean> test) {
        return (double) values.stream().filter(test::apply).count() / values.size();
    }
}
