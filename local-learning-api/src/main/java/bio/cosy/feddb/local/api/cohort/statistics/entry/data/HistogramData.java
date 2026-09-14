package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

import java.util.List;

public record HistogramData(List<Bin> bins) implements CustomStatisticData {

    public record Bin(double start, double end, long count) {}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.HISTOGRAM;
    }
}
