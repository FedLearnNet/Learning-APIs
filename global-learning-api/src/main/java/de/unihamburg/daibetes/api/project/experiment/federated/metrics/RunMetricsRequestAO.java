package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RunMetricsRequestAO implements PanacheRepository<RunMetricsRequestEntity> {

    public Optional<RunMetricsRequestEntity> findByExperimentId(Long experimentId) {
        return find("experiment.id", experimentId).firstResultOptional();
    }

    public Optional<RunMetricsRequestEntity> findByGlobalRequestId(UUID globalRequestId) {
        return find("globalRequestId", globalRequestId).firstResultOptional();
    }
}
