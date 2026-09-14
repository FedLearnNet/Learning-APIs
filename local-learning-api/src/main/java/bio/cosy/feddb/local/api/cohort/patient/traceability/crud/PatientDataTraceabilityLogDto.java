package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.RevisionType;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
public class PatientDataTraceabilityLogDto {
    private String id;

    private Long internalPatientId;

    private Integer revId;

    private Long dataEntriesVersion;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private Date createdAt;

    private String patientId;

    private String userId;

    private Long connectorId;

    private Long cohortId;

    private Long runId;

    private boolean dryRun;

    private boolean committed;

    private RevisionType changeType;


    public PatientDataTraceabilityLogDto(TracabilityLog log) {
        this.patientId = log.getExternalPatientId();
        this.internalPatientId = log.getInternalPatientId();
        this.revId = log.getRevisionId();
        this.dataEntriesVersion = log.getDataEntriesVersion();
        this.id = UUID.randomUUID().toString();
        this.userId = log.getKeycloakUserId();
        this.connectorId = log.getConnectorId();
        this.runId = log.getRunId();
        this.createdAt = Date.from(log.getChangeDate());
        this.changeType = log.getChangeType();
        this.cohortId = log.getCohortId();
        this.dryRun = false; //if audited then cant be dry run
        this.committed = true; //if audited then committed
    }
}
