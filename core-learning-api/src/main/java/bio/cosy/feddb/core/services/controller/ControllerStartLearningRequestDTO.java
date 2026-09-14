package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ControllerStartLearningRequestDTO {

    private String channel;
    private String clientId;
    private String clientKey;
    private String relayKey;
    private String runId;
    private String coordinatorId;
    private Integer maxNumClients;
    private List<String> orderClientIds;
    private String appKey = UUID.randomUUID().toString();
    private String appVersion;

}
