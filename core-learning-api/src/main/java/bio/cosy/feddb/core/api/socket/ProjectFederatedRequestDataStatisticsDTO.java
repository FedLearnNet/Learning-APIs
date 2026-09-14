package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectFederatedRequestDataStatisticsDTO {
    private String keycloakId;
    private String globalUniqueQueryId;
    private UUID globalRequestId;
}
