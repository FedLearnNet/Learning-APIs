package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Server-computed evaluation summary for a federated experiment, derived from the per-clinic per-round
 * metrics gathered via the metrics-request pipeline.
 *
 * Definitions (so "federated" is unambiguous):
 * <ul>
 *   <li><b>federated</b> = validation-size-weighted mean across sites of each site's <i>final-round</i>
 *       value. Every site evaluates the same converged global model on its own validation split, so
 *       weighting by n_val makes this equivalent to evaluating the global model on the pooled
 *       validation set.</li>
 *   <li><b>localMean</b> = validation-size-weighted mean across sites of each site's <b>standalone
 *       local model</b> (trained to convergence on that site's own data only) evaluated on the same
 *       validation split (the <code>local_val_*</code> metrics) — the isolated-training lower
 *       reference, measured on identical held-out rows for a clean paired comparison.</li>
 *   <li><b>benefit</b> = federated − localMean.</li>
 *   <li><b>federatedMin/Max/Sd</b> = spread of the per-site final values — cross-institution fairness
 *       of the global model.</li>
 *   <li><b>scalability</b> = operational cost (dimension D4): wall-clock per round, time spent in the
 *       aggregation round-trip, and the per-round payload size (model parameters). The payload is
 *       independent of cohort size, which is the core scalability argument.</li>
 *   <li><b>securedCounts</b> = the per-site n_train / n_val are privacy-rounded at the clinic (the
 *       platform's secure-count policy), so they are safe disclosures rather than exact patient
 *       numbers.</li>
 * </ul>
 */
@Data
public class EvaluationSummaryDTO {
    private String primaryMetric;
    private List<String> metricNames;
    private int rounds;
    private int siteCount;
    private boolean securedCounts;      // per-site counts are privacy-rounded at the clinic
    private Integer convergenceRound;   // first round reaching >= 99% of the final primary-metric value
    private List<MetricComparison> comparison;
    private List<RoundPoint> convergence;
    private List<SiteSummary> sites;
    private ScalabilitySummary scalability;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MetricComparison {
        private String metric;
        private Double localMean;
        private Double federated;
        private Double benefit;
        private Double federatedMin;
        private Double federatedMax;
        private Double federatedSd;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoundPoint {
        private int round;
        private double value;   // validation-size-weighted mean of the primary metric across sites
    }

    /**
     * Operational scalability for dimension D4. Per-round figures are means across sites; totals are
     * the sum over rounds of those means (i.e. the wall-clock a single site experiences end-to-end).
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScalabilitySummary {
        private Double avgRoundSeconds;     // mean wall-clock per round
        private Double totalRoundSeconds;   // end-to-end training wall-clock (sum of per-round means)
        private Double avgCommSeconds;      // mean time per round in the aggregate() round-trip
        private Double totalCommSeconds;
        private Integer paramsPerRound;     // model parameters exchanged each round (cohort-independent)
        private List<RoundTiming> timings;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoundTiming {
        private int round;
        private Double roundSeconds;        // mean across sites
        private Double commSeconds;         // mean across sites
        private Double cumulativeSeconds;   // running sum of roundSeconds — drives the cumulative graph
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SiteSummary {
        private String clinicId;
        private Integer nTrain;
        private Integer nVal;
        private Double positiveRate;            // readmission base rate in the site's validation split
        private Map<String, Double> federated;  // global model on this site's validation split, per metric
        private Map<String, Double> local;      // standalone local model on the same split, per metric
    }
}
