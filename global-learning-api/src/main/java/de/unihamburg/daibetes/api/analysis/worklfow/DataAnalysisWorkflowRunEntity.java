package de.unihamburg.daibetes.api.analysis.worklfow;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_workflow_runs")
public class DataAnalysisWorkflowRunEntity extends BaseWorkflowExperimentEntity<DataAnalysisWorkflowRunStepEntity> {

    @ManyToOne(cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowEntity workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_analysis_id")
    private DataAnalysisEntity dataAnalysis;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<DataAnalysisWorkflowRunStepEntity> steps;

    @Column(name = "keycloak_id", nullable = false)
    private String keycloakId;
}
