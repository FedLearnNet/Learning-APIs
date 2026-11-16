package de.unihamburg.daibetes.execution.message;

import bio.cosy.feddb.core.execution.message.BaseExecutionMessageAO;
import bio.cosy.feddb.core.execution.message.RunMessageTypes;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExecutionMessageAO extends BaseExecutionMessageAO<ExecutionMessageEntity> {

    public List<ExecutionMessageEntity> listForAttempt(Long attemptId, RunMessageTypes type) {
        return list("attempt.id = ?1 and type = ?2", Sort.by("id"), attemptId, type);
    }

    public Optional<ExecutionMessageEntity> findLatest(Long attemptId, RunMessageTypes type) {
        return find("attempt.id = ?1 and type = ?2", Sort.by("id", Sort.Direction.Descending), attemptId, type)
                .firstResultOptional();
    }
}
