package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentLearningClientSyncResponseDTO {
    private FedDBClientResponseType type;
    private List<String> currentGlobalUqLearningIds;

    public static CurrentLearningClientSyncResponseDTO createResponse(List<String> experiments) {
        CurrentLearningClientSyncResponseDTO dto = new CurrentLearningClientSyncResponseDTO();
        dto.setType(FedDBClientResponseType.CURRENT_LEARNINGS);
        dto.setCurrentGlobalUqLearningIds(experiments);
        return dto;
    }

}
