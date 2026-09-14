package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import java.util.List;

/**
 * The fixed set of validation metrics that flow from an app's {@code send_metric} calls into the
 * federated evaluation summary (the "Evaluation" tab). Declared first is the primary endpoint.
 *
 * <p>This enum is the backend half of a contract that is intentionally mirrored — and therefore
 * <em>offset</em>-duplicated — by the Python enum
 * {@code pyfedappwrap.learning.metrics.MetricName} / {@code EVAL_METRICS} that apps emit. The two live
 * in separate codebases; their string keys MUST stay in lock-step. See the "Metric contract" page in
 * the tool-developer documentation.</p>
 */
public enum EvaluationMetric {
    VAL_AUC("val_auc"),
    VAL_AUPRC("val_auprc"),
    VAL_F1("val_f1"),
    VAL_PRECISION("val_precision"),
    VAL_RECALL("val_recall"),
    VAL_ACCURACY("val_accuracy"),
    VAL_BRIER("val_brier");

    /** Prefix for the standalone institution-local baseline counterpart of a metric. */
    public static final String LOCAL_PREFIX = "local_";

    private final String key;

    EvaluationMetric(String key) {
        this.key = key;
    }

    /** On-the-wire metric name as emitted by the app (e.g. {@code val_auc}). */
    public String key() {
        return key;
    }

    /** Name of this metric's local-baseline counterpart (e.g. {@code local_val_auc}). */
    public String localKey() {
        return LOCAL_PREFIX + key;
    }

    /** The primary endpoint used for convergence and headline figures. */
    public static EvaluationMetric primary() {
        return VAL_AUC;
    }

    /** Ordered on-the-wire keys, primary first — used to drive the UI metric toggle. */
    public static List<String> keys() {
        return java.util.Arrays.stream(values()).map(EvaluationMetric::key).toList();
    }
}
