package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Computed data payload returned alongside a custom statistic. Stored as JSONB.
 * Shape is discriminated by a Jackson-managed {@code "type"} property.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BarChartData.class, name = "BAR"),
        @JsonSubTypes.Type(value = PieChartData.class, name = "PIE"),
        @JsonSubTypes.Type(value = LineChartData.class, name = "LINE"),
        @JsonSubTypes.Type(value = ScatterChartData.class, name = "SCATTER"),
        @JsonSubTypes.Type(value = HistogramData.class, name = "HISTOGRAM"),
        @JsonSubTypes.Type(value = BoxplotData.class, name = "BOXPLOT"),
        @JsonSubTypes.Type(value = HeatmapData.class, name = "HEATMAP"),
})
public sealed interface CustomStatisticData permits
        BarChartData,
        PieChartData,
        LineChartData,
        ScatterChartData,
        HistogramData,
        BoxplotData,
        HeatmapData {

    @JsonIgnore
    CustomStatisticType type();
}
