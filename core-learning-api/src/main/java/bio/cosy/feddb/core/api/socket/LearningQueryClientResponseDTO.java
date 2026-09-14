package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningQueryClientResponseDTO {
    private FedDBClientResponseType type;
    private String globalFLExperimentUniqueId;
    private String uniqueRandomClinicId;
    private Integer count;
    private String error;
    private Boolean modelCanBePublic;

    public static LearningQueryClientResponseDTO createErrorResponse(String errorMessage, String globalFLExperimentUniqueId) {
        LearningQueryClientResponseDTO dto = new LearningQueryClientResponseDTO();
        dto.setType(FedDBClientResponseType.ERROR);
        dto.setError(errorMessage);
        dto.setCount(0);
        dto.setUniqueRandomClinicId(null);
        dto.setGlobalFLExperimentUniqueId(globalFLExperimentUniqueId);
        return dto;
    }

    public static LearningQueryClientResponseDTO createResponse(Integer count, String globalFLExperimentUniqueId, String uniqueRandomClinicId) {
        LearningQueryClientResponseDTO dto = new LearningQueryClientResponseDTO();
        dto.setType(FedDBClientResponseType.LEARNING_RESPONSE);
        dto.setError(null);
        dto.setCount(count);
        dto.setUniqueRandomClinicId(uniqueRandomClinicId);
        dto.setGlobalFLExperimentUniqueId(globalFLExperimentUniqueId);
        return dto;
    }
}
