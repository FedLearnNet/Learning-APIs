package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.query.QueryEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "query_patient",
        uniqueConstraints = @UniqueConstraint(columnNames = {"patient_id", "query_id"}),
        indexes = {
                @Index(name = "idx_query_patient_query_id", columnList = "query_id")
        })
@Getter
@Setter
public class QueryPatientEntity extends BaseEntity {

    @ManyToOne(cascade = {CascadeType.REFRESH})
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @ManyToOne(cascade = {CascadeType.REFRESH})
    @JoinColumn(name = "query_id", nullable = false)
    private QueryEntity query;

    public QueryPatientEntity() {

    }

    public QueryPatientEntity(PatientEntity patient, QueryEntity query) {
        this.patient = patient;
        this.query = query;
    }
}
