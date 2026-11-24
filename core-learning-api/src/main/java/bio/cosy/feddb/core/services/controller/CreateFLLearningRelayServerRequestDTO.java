package bio.cosy.feddb.core.services.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFLLearningRelayServerRequestDTO {
    public Integer maxNumClients;
    public RelayServerAppVersions appVersion;

    public static CreateFLLearningRelayServerRequestDTO create(Integer maxNumClients) {
        return new CreateFLLearningRelayServerRequestDTO(maxNumClients, RelayServerAppVersions.v2);
    }

}
