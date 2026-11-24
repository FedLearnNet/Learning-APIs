package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sent by a clinic to the global server: asks the platform's Flower aggregator of one workflow step to register
 * the clinic SuperNode's public key. Shaped like {@link RelayCertSignRequestDTO}, which global checks the same
 * way; answered with a {@code FlowerSupernodeRegisteredDTO}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowerSupernodeKeyRequestDTO {
    // Chosen by the clinic's app, echoed in the answer
    private String requestId;
    private String globalUniqueExperimentId;
    private String uniqueRandomClinicId;
    // Workflow node of the step the Flower run belongs to
    private String currentNodeId;
    // OpenSSH EC public key of the clinic's SuperNode
    private String publicKey;
}
