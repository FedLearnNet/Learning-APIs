package bio.cosy.feddb.local.api.cohort.patient;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.query.QueryPatientEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "patient_meta",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"external_patient_id", "cohort_id"})
        },
        indexes = {
                @Index(
                        name = "idx_patient_meta_cohort_id",
                        columnList = "cohort_id"
                ),
                @Index(
                        name = "idx_patient_meta_cohort_id_id",
                        columnList = "cohort_id, id"
                )
        }
)
@Audited
@Getter
@Setter
public class PatientEntity extends BaseEntity {
    // As internal ID we use the ID from the BaseEntity!

    // This is the external patient ID that is used to identify the patient in the external system
    // Hashed and salted to protect the privacy of the patient
    // We have no control over the external ID, so we use a string to store it
    @Column(unique = false,
            name = "external_patient_id", length = 1024, nullable = false)
    private String externalPatientId;

    @ManyToOne(cascade = {CascadeType.REFRESH}, fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = true)
    @Audited
    private CohortEntity cohort;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @NotAudited
    private Set<QueryPatientEntity> queries = new java.util.HashSet<>();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @NotAudited
    private Set<PatientLearningEntity> learnings = new java.util.HashSet<>();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)  // This can be a LOT of data, so we lazy load it
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Audited
    @OrderBy("id ASC")
    private Set<PatientDataEntryEntity> dataEntries = new HashSet<>();

    @Column(name = "last_queried_at")
    @NotAudited
    private LocalDateTime lastQueriedAt;

    @Column(name = "data_entries_version", nullable = false)
    @ColumnDefault("0")
    @Audited
    private Long dataEntriesVersion = 0L;
    // Versioning that's just used for auditing purposes
    // It's increased whenever an endpoint updates the data entries
    // but doesn't add/remove any entries
    // hibernate automatically removes the version column from the BaseEntity
    // from the audit tables because of the @Version annotation, same happens with
    // the createdAt and updatedAt columns, so we need to add them manually here
}
