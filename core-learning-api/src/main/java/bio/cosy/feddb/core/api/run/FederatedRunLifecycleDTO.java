package bio.cosy.feddb.core.api.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

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
