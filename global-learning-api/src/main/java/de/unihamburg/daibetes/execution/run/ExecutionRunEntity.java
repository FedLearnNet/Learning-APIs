package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.development.engine.TestEngineMode;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.*;
import java.util.stream.Collectors;

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
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DataAnalysisEntity dataAnalysis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id")
    private ExecutionExperimentEntity experiment;

    // The ENGINE run whose container served this test run; none for the developer's local engine
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_engine_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ExecutionRunEntity testEngine;

    // What an ENGINE run's container is for
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_mode", length = 32)
    private TestEngineMode engineMode;

    // Who takes part in a federated run; the participant owns the link.
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ExecutionParticipantEntity> participants = new ArrayList<>();

    /** The project the run trains for, through its experiment. */
    public Optional<ProjectEntity> project() {
        return Optional.ofNullable(experiment).map(ExecutionExperimentEntity::getProject);
    }

    public ExecutionParticipantEntity addParticipant(ExecutionParticipantEntity participant) {
        participant.setRun(this);
        participants.add(participant);
        return participant;
    }

    public Optional<ExecutionParticipantEntity> participant(String participantId) {
        return participants.stream().filter(participant -> participantId.equals(participant.getParticipantId())).findFirst();
    }

    /** How far all participants got with the current step together. */
    public ProjectStatus participantStatus() {
        if (participants.isEmpty()) {
            return ProjectStatus.ERROR;
        }
        Map<ProjectStatus, Long> counts = participants.stream().collect(Collectors.groupingBy(
                participant -> Optional.ofNullable(participant.getStepStatus()).orElse(ProjectStatus.INIT),
                () -> new EnumMap<>(ProjectStatus.class), Collectors.counting()));
        long all = participants.size();
        if (counts.getOrDefault(ProjectStatus.FINISHED, 0L) == all) {
            return ProjectStatus.FINISHED;
        }
        for (ProjectStatus any : List.of(ProjectStatus.ERROR, ProjectStatus.SHUTDOWN, ProjectStatus.STOPPED)) {
            if (counts.containsKey(any)) {
                return any;
            }
        }
        if (counts.getOrDefault(ProjectStatus.RUNNING, 0L) == all) {
            return ProjectStatus.RUNNING;
        }
        if (counts.containsKey(ProjectStatus.PREPARE)) {
            return ProjectStatus.PREPARE;
        }
        return counts.getOrDefault(ProjectStatus.READY, 0L) == all ? ProjectStatus.READY : ProjectStatus.INIT;
    }
}
