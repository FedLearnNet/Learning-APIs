package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
}
