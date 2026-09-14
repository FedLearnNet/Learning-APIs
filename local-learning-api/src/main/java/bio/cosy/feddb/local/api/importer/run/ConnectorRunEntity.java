package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesEntity;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogEntity;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "connector_run",
        indexes = {
                @Index(name = "idx_connector_run_cohort_id", columnList = "cohort_id"),
                @Index(name = "idx_connector_run_connector_id", columnList = "connector_id")
        })
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorRunEntity extends BaseAuthEntity {

        @Column(name = "progess", nullable = false)
        private Long progress;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private ImportStatusEnum status = ImportStatusEnum.INIT;

        @Enumerated(EnumType.STRING)
        @Column(name = "current_step")
        private ConnectorRunStep currentStep;

        @Column(name = "progress_extracting")
        private Long progressExtracting; // rows loaded during extraction

        @Column(name = "current_transforming_step")
        private Long currentTransformingStep;

        @Column(name = "current_element_nr")
        private Long currentElementNr; // 1-based index of the patient currently being processed

        @Column(name = "current_row_nr")
        private Long currentRowNr; // cumulative number of rows processed so far

        @Column(name = "dry_run")
        private Boolean dryRun;

        @Column(name = "delete_existing_patients")
        private Boolean deleteExistingPatients;

        @Column(name = "expected_elements")
        private Long expectedElements; // total number of distinct patients to process

        @Column(name = "new_entities")
        private Long newEntities = 0L;

        @Column(name = "updated_entities")
        private Long updatedEntities = 0L;

        @Column(name = "deleted_entities")
        private Long deletedEntities = 0L;

        @Column(name = "failed_entities")
        private Long failedEntities = 0L;

        @Column(name = "unchanged_entities")
        private Long unchangedEntities = 0L;

        @Column(name = "received_entities")
        private Long receivedEntities = 0L;

        @Column(name = "processed_entities")
        private Long processedEntities = 0L;

        @Column(name = "new_data_entries")
        private Long newDataEntries = 0L;

        @Column(name = "failed_data_entries")
        private Long failedDataEntries = 0L;

        @Column(name = "error_message", length = 2000)
        private String errorMessage;

        @ManyToOne(optional = false)
        @JoinColumn(name = "cohort_id")
        private CohortEntity cohort;

        @ManyToOne(optional = false)
        @JoinColumn(name = "connector_id")
        private ConnectorEntity connector;

        @OneToMany(mappedBy = "connectorRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private List<ConnectorRunStepEntity> steps = new ArrayList<>();

        @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private List<ConnectorRunMessagesEntity> messages = new ArrayList<>();

        @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private List<ConnectorRunPatientLogEntity> errorLogs = new ArrayList<>();
}
