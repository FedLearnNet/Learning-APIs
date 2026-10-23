package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "execution_run")
public class ExecutionRunEntity extends BaseExecutionRunEntity<ExecutionRunStepEntity> {

    // User who started the run; set right after the run is created.
    @Column(name = "keycloak_id")
    private String keycloakId;

    @ManyToOne
    @JoinColumn(name = "workflow_id", nullable = true)
    private DataAnalysisEntity dataAnalysis;
}
