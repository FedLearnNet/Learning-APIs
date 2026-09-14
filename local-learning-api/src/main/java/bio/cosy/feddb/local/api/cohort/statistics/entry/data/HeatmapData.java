package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

import java.util.List;

public record HeatmapData(
        List<String> xLabels,
        List<String> yLabels,
        List<Cell> cells
) implements CustomStatisticData {

    /**
     * x and y are zero-based label indices into {@link #xLabels()} / {@link #yLabels()}.
     */
    public record Cell(int x, int y, double value) {}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.HEATMAP;
    }
}
