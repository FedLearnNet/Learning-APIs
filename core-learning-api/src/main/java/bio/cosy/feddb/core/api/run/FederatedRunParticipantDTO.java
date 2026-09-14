package bio.cosy.feddb.core.api.run;

import lombok.Data;

import java.util.LinkedHashMap;

/**
 * One participant entry in a {@link StartRunDTO} for a federated run, matching the app-side
 * (pyfedappwrap) {@code FederatedParticipantConfigDTO} schema. Each clinic sends itself as a
 * {@code CLIENT}; the coordinator clinic additionally sends an {@code AGGREGATOR}.
 */
@Data
public class FederatedRunParticipantDTO {
    /** Relay client id for this participant. */
    private String participantId;
    /** "CLIENT" or "AGGREGATOR". */
    private String role;
    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
}
