package de.unihamburg.daibetes.api.project.training.result;

import bio.cosy.feddb.core.api.socket.RunResultRequestType;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RunResultRequestAO implements PanacheRepository<RunResultRequestEntity> {

    public Optional<RunResultRequestEntity> findByExperimentId(Long experimentId, RunResultRequestType type) {
        return find("experiment.id = ?1 and type = ?2 order by id desc", experimentId, type).firstResultOptional();
    }

    public Optional<RunResultRequestEntity> findByGlobalRequestId(UUID globalRequestId) {
        return find("globalRequestId", globalRequestId).firstResultOptional();
    }
}
