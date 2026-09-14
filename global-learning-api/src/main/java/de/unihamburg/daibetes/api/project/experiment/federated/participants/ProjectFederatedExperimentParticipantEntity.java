package de.unihamburg.daibetes.api.project.experiment.federated.participants;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "project_federated_experiments_participants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"experiment_id", "unique_random_clinic_id"})
})
public class ProjectFederatedExperimentParticipantEntity extends BaseEntity {

    @Column(name = "unique_random_clinic_id")
    private String uniqueRandomClinicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_status")
    private RunStatusTypes projectStatus;

    @Column(name = "current_node_id")
    private String currentNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status")
    private ProjectStatus stepStatus;

    @Column(name = "model_can_be_public", columnDefinition = "boolean default false")
    private Boolean modelCanBePublic;

    @Column(name = "is_coordinator", columnDefinition = "boolean default false")
    private Boolean isCoordinator;

    /**
     * Null-safe accessor. The column is a nullable Boolean (legacy rows and non-coordinators may be
     * null), so callers must not unbox it directly. This overrides the Lombok getter and returns a
     * primitive boolean, treating "unset" as not-a-coordinator.
     */
    public boolean getIsCoordinator() {
        return Boolean.TRUE.equals(isCoordinator);
    }

    @ManyToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "experiment_id", nullable = false)
    private ProjectFederatedExperimentEntity experiment;
}
