package bio.cosy.feddb.core.api.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * Free-form run metadata, persisted as JSON (jsonb) on a run and mirrored on the run DTO.
 *
 * <p>This is the single, extensible place for per-run measurements a reproducibility reviewer might
 * ask for. New keys can be added here without a schema change (the column stays {@code jsonb}) and
 * historical rows simply lack the newer keys. Everything is nullable.
 *
 * <p>Populated today:
 * <ul>
 *   <li>{@link #timings} — lifecycle durations (ms) keyed by {@link RunTimingMetric}. RUNTIME is
 *       always present when measured; the OVERHEAD_* entries are gated by
 *       {@code posymed.runtime.overhead.enabled}.</li>
 * </ul>
 *
 * <p>Reserved for follow-up work (declared so the shape is stable and reviewers can see the intent;
 * not populated yet — see the out-of-scope notes in the timing spec):
 * <ul>
 *   <li>{@link #engineVersion} — wrapper/engine version, for exact reproducibility of the environment.</li>
 *   <li>{@link #peakMemoryMb} / {@link #avgCpuPercent} — resource usage (CPU/memory profiling is
 *       out of scope for the current timing work, but this is where it would live).</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunMetaDTO {

    /** Lifecycle durations in ms, keyed by metric. Populated now. */
    private Map<RunTimingMetric, Long> timings;

    // --- reserved for follow-up (nullable, not populated yet) ---
    private String engineVersion;
    private Long peakMemoryMb;
    private Double avgCpuPercent;

    /**
     * Applies the overhead visibility rule to {@link #timings}: when overhead is disabled the
     * OVERHEAD_* entries are dropped and only RUNTIME is kept. Returns this instance for chaining.
     */
    public RunMetaDTO withOverheadVisibility(boolean overheadEnabled) {
        this.timings = RunTimingMetric.applyOverheadVisibility(this.timings, overheadEnabled);
        return this;
    }

    /** Null-safe read of a single timing metric (ms). */
    public Long timing(RunTimingMetric metric) {
        return timings == null ? null : timings.get(metric);
    }
}
