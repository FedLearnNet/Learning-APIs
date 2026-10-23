package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "execution_run")
public class ExecutionRunEntity extends BaseExecutionRunEntity<ExecutionRunStepEntity> {
}
