package de.unihamburg.daibetes.api.analysis.worklfow;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentAO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DataAnalysisWorkflowRunAO extends BaseWorkflowExperimentAO<DataAnalysisWorkflowRunEntity> {

    public Optional<DataAnalysisWorkflowRunEntity> findById(Long id, Long dataAnalysisId, String keycloakId) {
        return find("dataAnalysis.id = ?1 and id = ?2 and keycloakId = ?3", dataAnalysisId, id, keycloakId).firstResultOptional();
    }
}
