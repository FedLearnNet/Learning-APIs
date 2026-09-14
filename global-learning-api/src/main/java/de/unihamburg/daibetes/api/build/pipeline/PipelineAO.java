package de.unihamburg.daibetes.api.build.pipeline;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@ApplicationScoped
public class PipelineAO implements PanacheRepository<PipelineEntity> {

    public Optional<PipelineEntity> findByIdAndSecretOptional(Long id, String secret, LockModeType lockMode) {
        return find("id = ?1 and secret = ?2", id, secret)
                .withLock(lockMode)
                .firstResultOptional();
    }
}
