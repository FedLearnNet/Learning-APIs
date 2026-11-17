package de.unihamburg.daibetes.api.project.training.result.response;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RunResultResponseAO implements PanacheRepository<RunResultResponseEntity> {

    public List<RunResultResponseEntity> listForMetricsRequest(Long metricsRequestId) {
        return list("request.id", metricsRequestId);
    }
}
