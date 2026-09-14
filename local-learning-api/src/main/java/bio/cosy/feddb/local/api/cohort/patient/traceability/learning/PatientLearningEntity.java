package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_learning",
        uniqueConstraints = @UniqueConstraint(columnNames = {"patient_id", "request_id"}),
        indexes = {
                @Index(name = "idx_patient_learning_request_id", columnList = "request_id")
        })
@Getter
@Setter
public class PatientLearningEntity extends BaseEntity {

    @ManyToOne(cascade = {CascadeType.REFRESH})
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @ManyToOne(cascade = {CascadeType.REFRESH})
    @JoinColumn(name = "request_id", nullable = false)
    private FederatedLearningRequestEntity request;
}
