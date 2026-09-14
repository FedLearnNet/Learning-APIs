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


    public static CreateFLLearningRelayServerRequestDTO createForV1(Integer maxNumClients) {
        return new CreateFLLearningRelayServerRequestDTO(maxNumClients, RelayServerAppVersions.v1);
    }

    public static CreateFLLearningRelayServerRequestDTO createForV2(Integer maxNumClients) {
        return new CreateFLLearningRelayServerRequestDTO(maxNumClients, RelayServerAppVersions.v2);
    }

    public static CreateFLLearningRelayServerRequestDTO create(Integer maxNumClients, boolean isV1) {
        return isV1 ? createForV1(maxNumClients) : createForV2(maxNumClients);
    }

}
