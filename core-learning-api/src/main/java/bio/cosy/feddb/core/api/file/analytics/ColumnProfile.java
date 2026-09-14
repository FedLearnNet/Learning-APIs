package bio.cosy.feddb.core.api.file.analytics;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>The {@code @JsonDeserialize} annotations below make Quarkus skip the reflection-free
 * serializer for this record, because its code generator only supports a fixed set of Jackson
 * annotations. The reflective fallback is intentional: the deserializer has to travel with the
 * type so a plain {@code ObjectMapper} - such as the one reading the {@code transformed_statistics}
 * jsonb column - can restore the entries. Registering it on a module or mixin would not cover
 * those callers.</p>
 */
public record ColumnProfile(
        String name,
        String type,              // INTEGER | NUMBER | BOOLEAN | DATETIME | TEXT
        long count,               // rows scanned
        long missing,
        int uniqueValues,        // may be null for large TEXT
        Double mean, Double std,
        Double min, Double p25, Double median, Double p75, Double max,
        @JsonDeserialize(contentUsing = StringIntegerEntryDeserializer.class)
        List<Map.Entry<String, Integer>> topCategories, // for categorical/text-ish
        @JsonDeserialize(contentUsing = StringIntegerEntryDeserializer.class)
        List<Map.Entry<String, Integer>> valueCounts // complete distinct-value frequencies for file mapping
) {
    public ColumnProfile(
            String name,
            String type,
            long count,
            long missing,
            int uniqueValues,
            Double mean,
            Double std,
            Double min,
            Double p25,
            Double median,
            Double p75,
            Double max,
            List<Map.Entry<String, Integer>> topCategories
    ) {
        this(name, type, count, missing, uniqueValues, mean, std, min, p25, median, p75, max,
                topCategories, null);
    }

    public ColumnProfile withName(String newName) {
        return Objects.equals(name, newName)
                ? this
                : new ColumnProfile(newName, type, count, missing, uniqueValues, mean, std, min, p25,
                        median, p75, max, topCategories, valueCounts);
    }
}
