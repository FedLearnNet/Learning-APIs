package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.file.ExecutionFileEntity;
import bio.cosy.feddb.local.execution.message.ExecutionMessageEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
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

    // Logs and metrics in the order the app reported them.
    @OneToMany(mappedBy = "attempt", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<ExecutionMessageEntity> messages = new ArrayList<>();

    // Files the app read and wrote.
    @OneToMany(mappedBy = "attempt", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<ExecutionFileEntity> executionFiles = new ArrayList<>();
}
