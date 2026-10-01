package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for the relay server's /sign-fl-run-cert: sign the PEM encoded CSR of a client of the FL run
 * identified by the channel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelaySignCertRequestDTO {
    private String channel;
    private String clientId;
    private String csr;
}
