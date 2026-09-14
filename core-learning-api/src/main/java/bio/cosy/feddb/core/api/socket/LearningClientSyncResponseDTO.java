package bio.cosy.feddb.core.api.socket;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningClientSyncResponseDTO {
    private FedDBClientResponseType type;
    private String error;

    private String uniqueRandomClinicId;
    private RunStatusTypes projectStatus;
    private String currentNodeId;
    private ProjectStatus stepStatus;
    private String globalUniqueExperimentId;

    public static LearningClientSyncResponseDTO createErrorResponse(String errorMessage, String globalUqExperimentId) {
        return createErrorResponse(errorMessage, globalUqExperimentId, "UNKNOWN_CLINIC_ID");
    }

    public static LearningClientSyncResponseDTO createErrorResponse(String errorMessage, String globalUqExperimentId, String uniqueRandomClinicId) {
        LearningClientSyncResponseDTO dto = new LearningClientSyncResponseDTO();
        dto.setType(FedDBClientResponseType.ERROR);
        dto.setError(errorMessage);
        dto.setGlobalUniqueExperimentId(globalUqExperimentId);
        dto.setUniqueRandomClinicId(uniqueRandomClinicId);
        dto.setStepStatus(ProjectStatus.ERROR);
        dto.setProjectStatus(RunStatusTypes.ERROR);
        return dto;
    }

    public static LearningClientSyncResponseDTO createResponse(String globalUqExperimentId, String uniqueRandomClinicId) {
        return createResponse(globalUqExperimentId, uniqueRandomClinicId, null);
    }

    public static LearningClientSyncResponseDTO createResponse(String globalUqExperimentId, String uniqueRandomClinicId, String currentNodeId) {
        LearningClientSyncResponseDTO dto = new LearningClientSyncResponseDTO();
        dto.setType(FedDBClientResponseType.LEARNING_SYNC);
        dto.setError(null);
        dto.setCurrentNodeId(currentNodeId);
        dto.setStepStatus(ProjectStatus.INIT);
        dto.setProjectStatus(RunStatusTypes.STARTED);
        dto.setGlobalUniqueExperimentId(globalUqExperimentId);
        dto.setUniqueRandomClinicId(uniqueRandomClinicId);
        return dto;
    }

    public static LearningClientSyncResponseDTO createResponse(String currentNodeId,
                                                               String globalUqExperimentId,
                                                               RunStatusTypes projectStatus,
                                                               ProjectStatus stepStatus,
                                                               String uniqueRandomClinicId) {
        LearningClientSyncResponseDTO dto = new LearningClientSyncResponseDTO();
        dto.setType(FedDBClientResponseType.LEARNING_SYNC);
        dto.setError(null);
        dto.setCurrentNodeId(currentNodeId);
        dto.setStepStatus(stepStatus);
        dto.setProjectStatus(projectStatus);
        dto.setGlobalUniqueExperimentId(globalUqExperimentId);
        dto.setUniqueRandomClinicId(uniqueRandomClinicId);
        return dto;
    }
}
