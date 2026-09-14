package bio.cosy.feddb.local.api.importer.run.patientlog;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "connector_run_error_logs",
        indexes = {
                @Index(name = "idx_connector_run_error_logs_run_id", columnList = "run_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ConnectorRunPatientLogEntity extends BaseEntity {

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "field")
    private String field;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "level", length = 20)
    private String level = "ERROR";

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", length = 20)
    private ConnectorRunPatientLogType logType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private ConnectorRunEntity run;
}
