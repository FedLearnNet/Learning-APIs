package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the controller's /start-learning. The controller only connects to the relay server once the
 * certificate signed for this CSR is passed to /start-relaying.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControllerStartLearningResponseDTO {
    private String csr;
}
