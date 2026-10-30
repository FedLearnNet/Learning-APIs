package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sent by a client to the global server: asks to have the CSR of the client's controller signed by the relay
 * server, for the relay run of one workflow step. The controller needs the certificate to connect to the relay.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayCertSignRequestDTO {
    private String globalUniqueExperimentId;
    private String uniqueRandomClinicId;
    // Workflow node of the step the relay run belongs to
    private String currentNodeId;
    // Relay client id that was assigned to this clinic for the step
    private String clientId;
    // PEM encoded certificate signing request
    private String csr;
}
