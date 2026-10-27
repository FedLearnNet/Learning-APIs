package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunType;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A run on this clinic: a tool reading or writing patient data of a cohort, an app transforming an import, or
 * this clinic's part of a training across clinics.
 */
@Setter
@Getter
@Entity
@Table(name = "execution_run", indexes = {
        @Index(name = "idx_execution_run_cohort_id", columnList = "cohort_id"),
        @Index(name = "idx_execution_run_connector_run_id", columnList = "connector_run_id")
})
public class ExecutionRunEntity extends BaseExecutionRunEntity<ExecutionRunStepEntity> {

    @Column(name = "keycloak_id")
    private String keycloakId;

    // Cohort whose patient data a tool reads or writes.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private CohortEntity cohort;

    // What a patient tool does with the data; null for any other run.
    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type")
    private PatientToolRunType toolType;

    // Import whose transformer or extractor the run is.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_run_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ConnectorRunEntity connectorRun;

    // This clinic's id in a training across clinics, as the platform knows it.
    @Column(name = "participant_id")
    private String participantId;
}
