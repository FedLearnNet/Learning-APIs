package bio.cosy.feddb.core.api.run;

import java.util.EnumMap;
import java.util.Map;

/**
 * Named metrics of the tool lifecycle timing, all measured in milliseconds.
 *
 * <p>Timings are carried as a {@code Map<RunTimingMetric, Long>} and stored as JSON (jsonb) so new
 * metrics can be added here without a schema change. {@link #RUNTIME} is the pure scientific compute
 * time and is always exposed; the {@code OVERHEAD_*} metrics are gated by the config flag
 * {@code posymed.runtime.overhead.enabled} (see {@link #applyOverheadVisibility}).
 */
public enum RunTimingMetric {

    /** Pure scientific compute time: the tool's function only. */
    RUNTIME(false),
    /** Container start + engine init + auth/connect + config & input validation. */
    OVERHEAD_STARTUP(true),
    /** Output transfer + engine reset/cleanup. */
    OVERHEAD_TEARDOWN(true),
    /** OVERHEAD_STARTUP + OVERHEAD_TEARDOWN. */
    OVERHEAD_TOTAL(true);

    private final boolean overhead;

    RunTimingMetric(boolean overhead) {
        this.overhead = overhead;
    }

    public boolean isOverhead() {
        return overhead;
    }

    /**
     * Returns a view of {@code timings} honouring the overhead flag: when disabled, the overhead
     * metrics are dropped and only {@link #RUNTIME} is kept; when enabled, the map is returned
     * unchanged. Null-safe.
     */
    public static Map<RunTimingMetric, Long> applyOverheadVisibility(
            Map<RunTimingMetric, Long> timings, boolean overheadEnabled) {
        if (timings == null || overheadEnabled) {
            return timings;
        }
        Map<RunTimingMetric, Long> visible = new EnumMap<>(RunTimingMetric.class);
        timings.forEach((metric, value) -> {
            if (!metric.isOverhead()) {
                visible.put(metric, value);
            }
        });
        return visible;
    }
}
