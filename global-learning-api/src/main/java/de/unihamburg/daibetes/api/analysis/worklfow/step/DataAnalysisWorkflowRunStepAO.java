package de.unihamburg.daibetes.api.analysis.worklfow.step;

import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepAO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DataAnalysisWorkflowRunStepAO extends BaseWorkflowStepAO<DataAnalysisWorkflowRunStepEntity> {

    public Optional<DataAnalysisWorkflowRunStepEntity> getByContainerId(String containerId, String keycloakId) {
        return find("containerId = ?1 and experiment.keycloakId = ?2", containerId, keycloakId).firstResultOptional();
    }


}
