package bio.cosy.feddb.core.execution.step;

import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import bio.cosy.feddb.core.execution.BaseExecutionStatusEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

/**
 * Shared step mapping. Concrete tables should enforce uniqueness on (run_id, workflow_node_id).
 * The workflow node must be a versioned/snapshotted definition, not a mutable editor node.
 * A step without a node runs a single app or model, which the concrete step entity names.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionRunStepEntity<
        Run extends BaseExecutionRunEntity<?>,
        Node extends BaseWorkflowNodeEntity,
        Attempt extends BaseExecutionAttemptEntity<?, ?>> extends BaseExecutionStatusEntity {

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private Run run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_node_id", updatable = false)
    private Node workflowNode;

    @Column(name = "name")
    private String name;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search", columnDefinition = "jsonb", updatable = false)
    private HyperParamSearchDTO search;

    // Input data the app starts with, e.g. values or references to input files.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs", columnDefinition = "jsonb", updatable = false)
    private LinkedHashMap<String, Object> inputs;

    // Input files the app reads from its own data directory, by input name.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_file_paths", columnDefinition = "jsonb", updatable = false)
    private LinkedHashMap<String, String> inputFilePaths;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_attempt_id")
    private Attempt selectedAttempt;

    @OneToMany(mappedBy = "step", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @OrderBy("trialIndex ASC")
    private Set<Attempt> attempts = new LinkedHashSet<>();


    public Attempt getSelectedAttempt() {
        if (selectedAttempt != null) {
            return selectedAttempt;
        }
        return attempts.stream()
                .max(Comparator.comparingInt(BaseExecutionAttemptEntity::getTrialIndex))
                .orElse(null);
    }
}
