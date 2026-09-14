package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

/**
 * Non-performance metric names an app emits: cohort descriptors (parsed once per site, no round axis)
 * and the per-round scalability / communication series. Mirrors the corresponding members of the
 * Python {@code pyfedappwrap.learning.metrics.MetricName} enum — keep the string keys in sync.
 */
public enum SiteDescriptor {
    // --- Cohort descriptors (privacy-safe; emitted once at x=0) ---------------------------------
    N_SAMPLES_TRAIN("n_samples_train"),
    N_SAMPLES_VAL("n_samples_val"),
    N_FEATURES("n_features"),
    VAL_POSITIVE_RATE("val_positive_rate"),

    // --- Scalability / communication (per-round series, dimension D4) ---------------------------
    ROUND_SECONDS("round_seconds"),
    COMM_SECONDS("comm_seconds"),
    N_PARAMS("n_params");

    private final String key;

    SiteDescriptor(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
