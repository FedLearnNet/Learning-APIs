package bio.cosy.feddb.local.api.learning.project.run.step;

import java.util.Date;

/**
 * A step waiting for the relay certificate of its controller: everything needed to (re)send the certificate
 * signing request to the global server.
 */
public record RelayCertRequest(Long stepId,
                               String globalUniqueExperimentId,
                               String uniqueRandomClinicId,
                               String nodeId,
                               String clientId,
                               String csr,
                               int attempts,
                               Date requestedAt) {
}
