package bio.cosy.feddb.core.execution.run.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

/**
 * One participant entry in a {@link StartRunDTO} for a federated run, matching the app-side
 * (pyfedappwrap) {@code FederatedParticipantConfigDTO} schema. Each clinic sends itself as a
 * {@code CLIENT}; the coordinator clinic additionally sends an {@code AGGREGATOR}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FederatedRunParticipantDTO {
    /** Relay client id for this participant. */
    private String participantId;
    /** "CLIENT" or "AGGREGATOR". */
    private String role;
    private LinkedHashMap<String, Object> hyperParams;
    private LinkedHashMap<String, String> inputFilePaths;
}
