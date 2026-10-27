package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederatedAttemptEntity;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "execution_attempt", uniqueConstraints = @UniqueConstraint(columnNames = {"step_id", "trial_index"}))
public class ExecutionAttemptEntity extends BaseExecutionAttemptEntity<ExecutionRunStepEntity, ExecutionMessageEntity> {

    // Result the app uploaded with its outputs.
    @Column(columnDefinition = "TEXT")
    private String result;

    @OneToMany(mappedBy = "attempt", fetch = FetchType.LAZY)
    private List<DataAnalysisFileEntity> files = new ArrayList<>();

    // Set for a participant's share of a federated step.
    @OneToOne(mappedBy = "attempt", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ExecutionFederatedAttemptEntity federation;

    /** The federation of the attempt, created when it has none yet. */
    public ExecutionFederatedAttemptEntity federationOrNew() {
        if (federation == null) {
            federation = new ExecutionFederatedAttemptEntity();
            federation.setAttempt(this);
        }
        return federation;
    }
}
