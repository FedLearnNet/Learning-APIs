package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.LinkedHashMap;

@Setter
@Getter
@Entity
@Table(name = "patient_tool_run",
        indexes = {
                @Index(name = "idx_patient_tool_run_cohort_id", columnList = "cohort_id")
        })
public class PatientToolRunEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status")
    private RunStatusTypes runStatus;

    @Column(name = "container_id", nullable = true)
    private String containerId;

    @Column(length = 2000, name = "last_error")
    private String lastError;

    @Column(name = "progress", nullable = true)
    private Float progress;

    @Column(name = "finished_at")
    private Date finishedAt;

    @Column(name = "started_at")
    private Date startedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type")
    private PatientToolRunType toolType;

    @Column(name = "hyper_params", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private LinkedHashMap<String, Object> hyperParams;

    @Column(name = "image", nullable = true)
    private String image;

    @Column(name = "global_app_version_id", nullable = true)
    private Long globalAPPVersionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id", nullable = true, foreignKey = @ForeignKey(name = "fk_patient_tool_run_cohort"))
    private CohortEntity cohort;

    @Column(name = "app_name", nullable = true)
    private String appName;

    private String error;

    @Override
    public String toString() {
        return "BaseWorkflowStepEntity{" +
                "stepStatus=" + runStatus +
                ", containerId='" + containerId + '\'' +
                ", lastError='" + lastError + '\'' +
                ", progress=" + progress +
                '}';
    }
}
