package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningClientStopRequestDTO {
    private FedDBClientResponseType type;
    private String globalUniqueLearningExperimentId;

    public static LearningClientStopRequestDTO createRequest(String requestId) {
        LearningClientStopRequestDTO dto = new LearningClientStopRequestDTO();
        dto.setType(FedDBClientResponseType.LEARNING_STOP);
        dto.setGlobalUniqueLearningExperimentId(requestId);
        return dto;
    }
}
