package bio.cosy.feddb.local.api.cohort.patient.tools.log;

import bio.cosy.feddb.core.api.run.message.BaseRunMessageEntity;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "patient_tool_run_log",
        indexes = {
                @Index(name = "idx_patient_tool_run_log_run_id", columnList = "run_id")
        })
public class PatientToolRunLogEntity extends BaseRunMessageEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_id", nullable = false)
    private PatientToolRunEntity run;

}
