package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for the controller's /stop-learning. The run is identified by channel and app key.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControllerStopLearningRequestDTO {
    private String channel;
    private String appKey;
}
