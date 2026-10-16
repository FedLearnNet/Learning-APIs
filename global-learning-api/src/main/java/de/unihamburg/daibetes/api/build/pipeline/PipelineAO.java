package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineAO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PipelineAO extends BasePipelineAO<PipelineEntity> {
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PipelineAO implements PanacheRepository<PipelineEntity> {

    public boolean existsActiveByAppVersionId(Long appVersionId) {
        return count("appVersion.id = ?1 and pipelineStatus in ?2", appVersionId,
                List.of(PipelineStatus.PENDING, PipelineStatus.RUNNING)) > 0;
    }

    public long detachAppVersion(Long appVersionId) {
        return update("appVersion = null where appVersion.id = ?1", appVersionId);
    }

    public Optional<PipelineEntity> findByIdAndSecretOptional(Long id, String secret, LockModeType lockMode) {
        return find("id = ?1 and secret = ?2", id, secret)
                .withLock(lockMode)
                .firstResultOptional();
    }
}
