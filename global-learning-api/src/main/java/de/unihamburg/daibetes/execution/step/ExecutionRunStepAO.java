package de.unihamburg.daibetes.execution.step;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ExecutionRunStepAO extends BaseExecutionRunStepAO<ExecutionRunStepEntity> {

    // Steps that run a single app or model, i.e. runs without a workflow.
    public List<ExecutionRunStepEntity> listSingle(ExecutionPurpose purpose, String keycloakId) {
        return list("workflowNode is null and run.purpose = ?1 and run.keycloakId = ?2 order by id", purpose, keycloakId);
    }

    public List<ExecutionRunStepEntity> listSingleForModel(ExecutionPurpose purpose, String keycloakId, Long modelId) {
        return list("workflowNode is null and run.purpose = ?1 and run.keycloakId = ?2 and subModel.modelVersion.model.id = ?3 order by id",
                purpose, keycloakId, modelId);
    }

    public List<Long> listRunIdsForSubModel(Long subModelId) {
        return getEntityManager()
                .createQuery("select distinct s.run.id from ExecutionRunStepEntity s where s.subModel.id = :subModelId", Long.class)
                .setParameter("subModelId", subModelId)
                .getResultList();
    }
}
