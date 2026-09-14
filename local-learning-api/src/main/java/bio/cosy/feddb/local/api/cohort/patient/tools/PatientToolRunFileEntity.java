package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.base.BaseFileEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "patient_tool_run_file",
        indexes = {
                @Index(name = "idx_patient_tool_run_file_run_id", columnList = "run_id")
        })
public class PatientToolRunFileEntity extends BaseFileEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_id", nullable = false)
    private PatientToolRunEntity run;
}
