package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectFederatedRequestRunMetricsDTO {
    private String keycloakId;
    private String globalExperimentUniqueId;
    private UUID globalRequestId;
}
