package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import bio.cosy.feddb.core.api.socket.ProjectFederatedRequestRunMetricsDTO;
import bio.cosy.feddb.core.api.socket.RunMetricDTO;
import bio.cosy.feddb.core.api.socket.RunMetricsResponseClientDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentAO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseBO;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.response.RunMetricsResponseDTO;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@ApplicationScoped
public class RunMetricsRequestBO extends BaseBo<RunMetricsRequestDTO, RunMetricsRequestEntity, RunMetricsRequestAO, RunMetricsRequestMapper> {

    @Inject
    RunMetricsResponseBO runMetricsResponseBO;

    @Inject
    ProjectFederatedExperimentAO experimentAO;

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    ProjectMembershipAO membershipAO;

    public RunMetricsRequestDTO createAndBroadcast(Long experimentId, String keycloakId) {
        ProjectFederatedExperimentEntity experiment = experimentAO.findByIdOptional(experimentId)
                .orElseThrow(() -> new NotFoundException("Experiment not found: " + experimentId));

        RunMetricsRequestEntity entity = new RunMetricsRequestEntity();
        entity.setGlobalRequestId(UUID.randomUUID());
        entity.setRequestKeycloakId(keycloakId);
        entity.setExperiment(experiment);
        ao.persist(entity);

        ProjectFederatedRequestRunMetricsDTO broadcastMsg = new ProjectFederatedRequestRunMetricsDTO();
        broadcastMsg.setKeycloakId(keycloakId);
        broadcastMsg.setGlobalExperimentUniqueId(experiment.getGlobalUniqueId());
        broadcastMsg.setGlobalRequestId(entity.getGlobalRequestId());
        broadcastBO.fireRunMetricsRequest(broadcastMsg);

        return mapper.entityToDto(entity);
    }

    public Optional<RunMetricsRequestDTO> findForExperiment(Long experimentId, String keycloakId) {

        return ao.findByExperimentId(experimentId)
                .map(mapper::entityToDto)
                .map(r -> {
                    membershipAO.checkProjectAndUser(r.getProjectId(), keycloakId);
                    return r;
                });
    }

    public List<RunMetricsResponseDTO> listResponses(Long metricsRequestId) {
        return runMetricsResponseBO.listForMetricsRequest(metricsRequestId);
    }

    public void saveResponse(RunMetricsResponseClientDTO request) {
        ao.findByGlobalRequestId(request.getRequestId()).ifPresentOrElse(
                metricsRequest -> runMetricsResponseBO.saveResponse(request, metricsRequest),
                () -> Log.errorf("RunMetricsRequest with globalRequestId %s not found", request.getRequestId())
        );
    }

    // Evaluation metrics aggregated for the manuscript; sourced from the EvaluationMetric enum so the
    // backend and the apps (pyfedappwrap MetricName) share one canonical, ordered contract.
    private static final List<String> EVAL_METRICS = EvaluationMetric.keys();
    private static final String PRIMARY_METRIC = EvaluationMetric.primary().key();

    /**
     * Computes the evaluation summary for an experiment from the collected per-clinic per-round metrics.
     * "federated" and "local" are validation-size-weighted means across sites (see EvaluationSummaryDTO).
     */
    public EvaluationSummaryDTO evaluationSummary(Long experimentId, String keycloakId) {
        RunMetricsRequestDTO request = findForExperiment(experimentId, keycloakId)
                .orElseThrow(() -> new NotFoundException("No metrics request for experiment " + experimentId));
        List<RunMetricsResponseDTO> responses = request.getResponses() != null ? request.getResponses() : List.of();

        EvaluationSummaryDTO summary = new EvaluationSummaryDTO();
        summary.setPrimaryMetric(PRIMARY_METRIC);
        summary.setSiteCount(responses.size());

        List<SiteParsed> sites = new ArrayList<>();
        Set<String> metricNames = new LinkedHashSet<>();
        int maxRound = 0;
        for (RunMetricsResponseDTO r : responses) {
            SiteParsed sp = new SiteParsed();
            sp.clinicId = r.getRandomClinicId();
            for (RunMetricDTO m : Optional.ofNullable(r.getMetrics()).orElseGet(List::of)) {
                Double value = parseDouble(m.getValue());
                if (value == null) continue;
                String name = m.getMetric();
                // Cohort descriptors carry no round axis; everything else (performance + scalability
                // series like round_seconds/comm_seconds/n_params) is stored per round.
                if (SiteDescriptor.N_SAMPLES_VAL.key().equals(name)) {
                    sp.nVal = value.intValue();
                } else if (SiteDescriptor.N_SAMPLES_TRAIN.key().equals(name)) {
                    sp.nTrain = value.intValue();
                } else if (SiteDescriptor.VAL_POSITIVE_RATE.key().equals(name)) {
                    sp.positiveRate = value;
                } else {
                    Integer round = parseInt(m.getX());
                    if (round == null) continue;
                    sp.series.computeIfAbsent(name, k -> new TreeMap<>()).put(round, value);
                    metricNames.add(name);
                    maxRound = Math.max(maxRound, round);
                }
            }
            sp.weight = sp.nVal != null && sp.nVal > 0 ? sp.nVal : 1.0;
            sites.add(sp);
        }

        // Only the headline val_* metrics are offered in the UI toggle (ordered); the local_val_* and
        // descriptor series feed the computation but are not selectable.
        summary.setMetricNames(EVAL_METRICS.stream().filter(metricNames::contains).toList());
        summary.setRounds(maxRound);

        List<EvaluationSummaryDTO.MetricComparison> comparison = new ArrayList<>();
        for (String metric : EVAL_METRICS) {
            String localName = EvaluationMetric.LOCAL_PREFIX + metric;   // e.g. val_auc -> local_val_auc
            List<Double> finals = new ArrayList<>();
            List<Double> finalWeights = new ArrayList<>();
            List<Double> locals = new ArrayList<>();
            List<Double> localWeights = new ArrayList<>();
            for (SiteParsed sp : sites) {
                TreeMap<Integer, Double> fed = sp.series.get(metric);
                if (fed != null && !fed.isEmpty()) {
                    finals.add(fed.lastEntry().getValue());     // global model on this site's val split
                    finalWeights.add(sp.weight);
                }
                TreeMap<Integer, Double> loc = sp.series.get(localName);
                if (loc != null && !loc.isEmpty()) {
                    locals.add(loc.lastEntry().getValue());     // standalone local model on the same split
                    localWeights.add(sp.weight);
                }
            }
            if (finals.isEmpty()) continue;
            Double federated = weightedMean(finals, finalWeights);
            Double localMean = locals.isEmpty() ? null : weightedMean(locals, localWeights);
            comparison.add(new EvaluationSummaryDTO.MetricComparison(
                    metric, localMean, federated,
                    federated != null && localMean != null ? federated - localMean : null,
                    min(finals), max(finals), sd(finals)));
        }
        summary.setComparison(comparison);

        List<EvaluationSummaryDTO.RoundPoint> convergence = new ArrayList<>();
        for (int round = 1; round <= maxRound; round++) {
            List<Double> vals = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (SiteParsed sp : sites) {
                TreeMap<Integer, Double> s = sp.series.get(PRIMARY_METRIC);
                Double v = s == null ? null : s.get(round);
                if (v == null) continue;
                vals.add(v);
                weights.add(sp.weight);
            }
            Double wm = weightedMean(vals, weights);
            if (wm != null) convergence.add(new EvaluationSummaryDTO.RoundPoint(round, wm));
        }
        summary.setConvergence(convergence);
        summary.setConvergenceRound(convergenceRound(convergence));

        List<EvaluationSummaryDTO.SiteSummary> siteSummaries = new ArrayList<>();
        for (SiteParsed sp : sites) {
            Map<String, Double> fed = new LinkedHashMap<>();
            Map<String, Double> loc = new LinkedHashMap<>();
            for (String metric : EVAL_METRICS) {
                TreeMap<Integer, Double> fs = sp.series.get(metric);
                if (fs != null && !fs.isEmpty()) fed.put(metric, fs.lastEntry().getValue());
                TreeMap<Integer, Double> ls = sp.series.get(EvaluationMetric.LOCAL_PREFIX + metric);
                if (ls != null && !ls.isEmpty()) loc.put(metric, ls.lastEntry().getValue());
            }
            siteSummaries.add(new EvaluationSummaryDTO.SiteSummary(
                    sp.clinicId, sp.nTrain, sp.nVal, sp.positiveRate, fed, loc));
        }
        summary.setSites(siteSummaries);

        // Cohort counts are privacy-rounded at the clinic (secure-count policy) before they are emitted.
        summary.setSecuredCounts(true);
        summary.setScalability(buildScalability(sites, maxRound));

        return summary;
    }

    /** Operational scalability (dimension D4): per-round wall-clock, aggregation round-trip time and
     *  payload size, averaged across sites. Returns null when no timing series were reported. */
    private EvaluationSummaryDTO.ScalabilitySummary buildScalability(List<SiteParsed> sites, int maxRound) {
        List<EvaluationSummaryDTO.RoundTiming> timings = new ArrayList<>();
        double totalRound = 0, totalComm = 0, cumulative = 0;
        int roundCount = 0, commCount = 0;
        for (int round = 1; round <= maxRound; round++) {
            Double rs = meanAcrossSites(sites, SiteDescriptor.ROUND_SECONDS.key(), round);
            Double cs = meanAcrossSites(sites, SiteDescriptor.COMM_SECONDS.key(), round);
            if (rs == null && cs == null) continue;
            if (rs != null) {
                totalRound += rs;
                cumulative += rs;
                roundCount++;
            }
            if (cs != null) {
                totalComm += cs;
                commCount++;
            }
            timings.add(new EvaluationSummaryDTO.RoundTiming(
                    round, rs, cs, rs != null ? cumulative : null));
        }
        if (timings.isEmpty()) return null;
        return new EvaluationSummaryDTO.ScalabilitySummary(
                roundCount > 0 ? totalRound / roundCount : null,
                roundCount > 0 ? totalRound : null,
                commCount > 0 ? totalComm / commCount : null,
                commCount > 0 ? totalComm : null,
                lastIntAcrossSites(sites, SiteDescriptor.N_PARAMS.key()),
                timings);
    }

    private static Double meanAcrossSites(List<SiteParsed> sites, String metric, int round) {
        List<Double> vals = new ArrayList<>();
        for (SiteParsed sp : sites) {
            TreeMap<Integer, Double> s = sp.series.get(metric);
            Double v = s == null ? null : s.get(round);
            if (v != null) vals.add(v);
        }
        return vals.isEmpty() ? null : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Integer lastIntAcrossSites(List<SiteParsed> sites, String metric) {
        for (SiteParsed sp : sites) {
            TreeMap<Integer, Double> s = sp.series.get(metric);
            if (s != null && !s.isEmpty()) return (int) Math.round(s.lastEntry().getValue());
        }
        return null;
    }

    /**
     * Serialises the evaluation summary as a single, sectioned CSV "results bundle" for the manuscript
     * (each section is a {@code # header} block: summary, metric comparison, convergence, scalability,
     * per-site). Reuses {@link #evaluationSummary} so the exported numbers are identical to the tab.
     */
    public String evaluationCsv(Long experimentId, String keycloakId) {
        EvaluationSummaryDTO s = evaluationSummary(experimentId, keycloakId);
        StringBuilder sb = new StringBuilder();

        sb.append("# Evaluation summary\n");
        sb.append("experimentId,").append(experimentId).append('\n');
        sb.append("primaryMetric,").append(csv(s.getPrimaryMetric())).append('\n');
        sb.append("rounds,").append(s.getRounds()).append('\n');
        sb.append("siteCount,").append(s.getSiteCount()).append('\n');
        sb.append("securedCounts,").append(s.isSecuredCounts()).append('\n');
        sb.append("convergenceRound,").append(s.getConvergenceRound() == null ? "" : s.getConvergenceRound()).append('\n');
        sb.append('\n');

        sb.append("# Metric comparison (validation-size-weighted across sites)\n");
        sb.append("metric,local_mean,federated,benefit,federated_min,federated_max,federated_sd\n");
        if (s.getComparison() != null) {
            for (EvaluationSummaryDTO.MetricComparison c : s.getComparison()) {
                sb.append(csv(c.getMetric())).append(',')
                        .append(num(c.getLocalMean())).append(',')
                        .append(num(c.getFederated())).append(',')
                        .append(num(c.getBenefit())).append(',')
                        .append(num(c.getFederatedMin())).append(',')
                        .append(num(c.getFederatedMax())).append(',')
                        .append(num(c.getFederatedSd())).append('\n');
            }
        }
        sb.append('\n');

        sb.append("# Convergence (weighted primary metric per round)\n");
        sb.append("round,").append(csv(s.getPrimaryMetric())).append('\n');
        if (s.getConvergence() != null) {
            for (EvaluationSummaryDTO.RoundPoint p : s.getConvergence()) {
                sb.append(p.getRound()).append(',').append(num(p.getValue())).append('\n');
            }
        }
        sb.append('\n');

        EvaluationSummaryDTO.ScalabilitySummary sc = s.getScalability();
        sb.append("# Scalability per round (mean across sites)\n");
        sb.append("round,round_seconds,comm_seconds,cumulative_seconds\n");
        if (sc != null && sc.getTimings() != null) {
            for (EvaluationSummaryDTO.RoundTiming t : sc.getTimings()) {
                sb.append(t.getRound()).append(',')
                        .append(num(t.getRoundSeconds())).append(',')
                        .append(num(t.getCommSeconds())).append(',')
                        .append(num(t.getCumulativeSeconds())).append('\n');
            }
        }
        sb.append('\n');
        sb.append("# Scalability summary\n");
        sb.append("avg_round_seconds,total_round_seconds,avg_comm_seconds,total_comm_seconds,params_per_round\n");
        if (sc != null) {
            sb.append(num(sc.getAvgRoundSeconds())).append(',')
                    .append(num(sc.getTotalRoundSeconds())).append(',')
                    .append(num(sc.getAvgCommSeconds())).append(',')
                    .append(num(sc.getTotalCommSeconds())).append(',')
                    .append(sc.getParamsPerRound() == null ? "" : sc.getParamsPerRound()).append('\n');
        }
        sb.append('\n');

        sb.append("# Per-site cohorts & performance (counts are privacy-rounded)\n");
        StringBuilder header = new StringBuilder("clinic_id,n_train,n_val,positive_rate");
        for (String m : EVAL_METRICS) header.append(",fed_").append(m);
        for (String m : EVAL_METRICS) header.append(",local_").append(m);
        sb.append(header).append('\n');
        if (s.getSites() != null) {
            for (EvaluationSummaryDTO.SiteSummary site : s.getSites()) {
                sb.append(csv(site.getClinicId())).append(',')
                        .append(site.getNTrain() == null ? "" : site.getNTrain()).append(',')
                        .append(site.getNVal() == null ? "" : site.getNVal()).append(',')
                        .append(num(site.getPositiveRate()));
                for (String m : EVAL_METRICS) {
                    sb.append(',').append(num(site.getFederated() == null ? null : site.getFederated().get(m)));
                }
                for (String m : EVAL_METRICS) {
                    sb.append(',').append(num(site.getLocal() == null ? null : site.getLocal().get(m)));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String num(Double d) {
        return d == null || d.isNaN() ? "" : java.math.BigDecimal.valueOf(d).toPlainString();
    }

    /** Minimal RFC-4180 field escaping for the few free-text fields (metric names, clinic ids). */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private static final class SiteParsed {
        String clinicId;
        final Map<String, TreeMap<Integer, Double>> series = new HashMap<>();
        double weight = 1.0;
        Integer nVal;
        Integer nTrain;
        Double positiveRate;
    }

    private static Double parseDouble(String s) {
        try {
            return s == null ? null : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        try {
            return s == null ? null : (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double weightedMean(List<Double> values, List<Double> weights) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < values.size(); i++) {
            num += values.get(i) * weights.get(i);
            den += weights.get(i);
        }
        return den == 0 ? null : num / den;
    }

    private static Double min(List<Double> v) {
        return v.isEmpty() ? null : v.stream().min(Double::compare).orElse(null);
    }

    private static Double max(List<Double> v) {
        return v.isEmpty() ? null : v.stream().max(Double::compare).orElse(null);
    }

    private static Double sd(List<Double> v) {
        if (v.size() < 2) return 0.0;
        double mean = v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = v.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / v.size();
        return Math.sqrt(var);
    }

    private static Integer convergenceRound(List<EvaluationSummaryDTO.RoundPoint> conv) {
        if (conv.isEmpty()) return null;
        double finalVal = conv.get(conv.size() - 1).getValue();
        for (EvaluationSummaryDTO.RoundPoint p : conv) {
            if (finalVal != 0 && p.getValue() >= 0.99 * finalVal) return p.getRound();
        }
        return conv.get(conv.size() - 1).getRound();
    }
}
