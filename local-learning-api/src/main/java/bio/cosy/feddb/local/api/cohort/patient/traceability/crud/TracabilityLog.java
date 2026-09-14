package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionType;

@Data
@Getter
@Setter
public class TracabilityLog {

    private Long internalPatientId;
    private Integer revisionId;
    private Long dataEntriesVersion;

    @NotNull
    private Instant changeDate;
    @NotNull
    private Long cohortId;
    @NotNull
    private String externalPatientId;
    @NotNull
    private RevisionType changeType;
    @NotNull
    private String keycloakUserId;
    private Long connectorId;
    private Long runId;

}
