package de.unihamburg.daibetes.api.project.experiment.federated.metrics.response;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RunMetricsResponseAO implements PanacheRepository<RunMetricsResponseEntity> {

    public List<RunMetricsResponseEntity> listForMetricsRequest(Long metricsRequestId) {
        return list("request.id", metricsRequestId);
    }
}
