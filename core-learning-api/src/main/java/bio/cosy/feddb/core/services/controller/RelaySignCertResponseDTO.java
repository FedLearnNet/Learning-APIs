package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the relay server's /sign-fl-run-cert: the PEM encoded client certificate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelaySignCertResponseDTO {
    private String certificate;
}
