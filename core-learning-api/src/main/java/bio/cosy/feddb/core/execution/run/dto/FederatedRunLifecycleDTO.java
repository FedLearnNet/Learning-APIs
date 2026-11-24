package bio.cosy.feddb.core.execution.run.dto;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

/**
 * Lifecycle message body sent by the app for federated runs (UPDATE_FEDERATED_RUN /
 * FINISH_FEDERATED_RUN, app-side {@code FederatedTestRunCreateDTO}). The app sends many more fields
 * (config, participants, outputData, ...) than the server needs, so unknown properties are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FederatedRunLifecycleDTO {
    private Long id;
    private String status;
    private String error;
    private Integer currentRound;

    // Flower-only: the platform's aggregator attempt reports a SuperLink CA (certificate and
    // private key, PEM text) once, only when it had to generate a new one - none stored yet, or
    // the stored one was about to expire. See BaseAttemptAppServer's UPDATE_FEDERATED_RUN
    // handling and FlowerPlatformCertificateAO on the global side. Null for every other message
    // (clinics, FL-Net, and every non-federated run never set them).
    private String caCertificate;
    @ToString.Exclude
    private String caPrivateKey;

    /** App statuses are upper-case strings (STARTED/RUNNING/FINISHED/ERROR); null when unknown. */
    public RunStatusTypes toRunStatus() {
        if (status == null) {
            return null;
        }
        try {
            return RunStatusTypes.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
