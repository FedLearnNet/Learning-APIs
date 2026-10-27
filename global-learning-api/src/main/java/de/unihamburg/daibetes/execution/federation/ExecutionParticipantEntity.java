package de.unihamburg.daibetes.execution.federation;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.run.types.FederatedParticipantType;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;

@Setter
@Getter
@Entity
@Table(name = "execution_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "participant_id"}))
public class ExecutionParticipantEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private ExecutionRunEntity run;

    // Relay client id of the participant, for a clinic its random clinic id.
    @Column(name = "participant_id", nullable = false, updatable = false)
    private String participantId;

    @Enumerated(EnumType.STRING)
    private FederatedParticipantType role;

    // The clinic that coordinates the run, it also runs the aggregator.
    @Column(nullable = false)
    private boolean coordinator;

    @Column(name = "model_can_be_public")
    private Boolean modelCanBePublic;

    // Input files the participant reads from its own data directory, by input name.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_file_paths", columnDefinition = "jsonb")
    private LinkedHashMap<String, String> inputFilePaths;

    // What a clinic reports: how its run goes, which workflow node it is at and how far that step got.
    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(name = "current_node_id")
    private String currentNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status")
    private ProjectStatus stepStatus;
}
