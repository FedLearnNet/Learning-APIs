package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Typed config payload for a {@link bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticEntity}.
 * Serialised as JSONB on disk via the entity. Jackson writes a {@code "type"} discriminator
 * as a JSON property (default {@link JsonTypeInfo.As#PROPERTY}); on deserialisation it
 * consumes the discriminator before passing the rest to the matching record constructor.
 * <p>
 * The records intentionally do <em>not</em> include {@code type} as a record component
 * (only Jackson writes it). The {@link #type()} accessor is {@link JsonIgnore}d so it
 * doesn't compete with the Jackson-managed discriminator.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BarChartConfig.class, name = "BAR"),
        @JsonSubTypes.Type(value = PieChartConfig.class, name = "PIE"),
        @JsonSubTypes.Type(value = LineChartConfig.class, name = "LINE"),
        @JsonSubTypes.Type(value = ScatterChartConfig.class, name = "SCATTER"),
        @JsonSubTypes.Type(value = HistogramConfig.class, name = "HISTOGRAM"),
        @JsonSubTypes.Type(value = BoxplotConfig.class, name = "BOXPLOT"),
        @JsonSubTypes.Type(value = HeatmapConfig.class, name = "HEATMAP"),
})
public sealed interface CustomStatisticConfig permits
        BarChartConfig,
        PieChartConfig,
        LineChartConfig,
        ScatterChartConfig,
        HistogramConfig,
        BoxplotConfig,
        HeatmapConfig {

    @JsonIgnore
    CustomStatisticType type();
}
