package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for the relay server's /stop-fl-run.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayStopRequestDTO {
    private String channel;
}
