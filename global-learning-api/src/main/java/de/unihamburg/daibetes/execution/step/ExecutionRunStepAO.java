package de.unihamburg.daibetes.execution.step;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExecutionRunStepAO extends BaseExecutionRunStepAO<ExecutionRunStepEntity> {

    @Override
    public Optional<ExecutionRunStepEntity> findByIdWrite(Long id) {
        return find("select id from ExecutionRunStepEntity where id = ?1", id)
                .project(Long.class)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional()
                .flatMap(this::findByIdOptional);
    }

    public Optional<ExecutionRunStepEntity> findTestStepOfAttemptWrite(Long attemptId) {
        return find("select step.id from ExecutionAttemptEntity where id = ?1 and step.run.purpose = ?2",
                attemptId, ExecutionPurpose.TEST)
                .project(Long.class)
                .firstResultOptional()
                .flatMap(this::findByIdWrite);
    }

    // Steps that run a single app or model, i.e. runs without a workflow.
    public List<ExecutionRunStepEntity> listSingle(ExecutionPurpose purpose, String keycloakId) {
        return list("workflowNode is null and run.purpose = ?1 and run.keycloakId = ?2 order by id", purpose, keycloakId);
    }

    public List<ExecutionRunStepEntity> listSingleForModel(ExecutionPurpose purpose, String keycloakId, Long modelId) {
        return list("workflowNode is null and run.purpose = ?1 and run.keycloakId = ?2 and subModel.modelVersion.model.id = ?3 order by id",
                purpose, keycloakId, modelId);
    }

    public List<Long> listRunIdsForSubModel(Long subModelId) {
        return find("select distinct run.id from ExecutionRunStepEntity where subModel.id = ?1", subModelId)
                .project(Long.class).list();
    }

    public Optional<ExecutionRunStepEntity> findProjectTrainingStepOptional(Long stepId) {
        return find("id = ?1 and run.purpose = ?2 and run.experiment.test = false", stepId, ExecutionPurpose.TRAINING).firstResultOptional();
    }
}
