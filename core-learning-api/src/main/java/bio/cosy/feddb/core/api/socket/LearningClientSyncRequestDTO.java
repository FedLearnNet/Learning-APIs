package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing a request to synchronize the state of a learning client.
 * This class is utilized in communication between a client and a centralized system during learning processes.
 * It encapsulates information about the type of synchronization, the next step in the process,
 * whether learning should start running, and the identifier of the learning request.
 *
 * The class provides static factory methods to streamline the creation of specific synchronization requests:
 * 1. {@code createNextRequest} - Used to create a request for the next step in a learning process.
 * 2. {@code createRunRequest} - Used to create a request that starts or resumes the learning process.
 *
 * Both methods ensure that the type of the sync request is appropriately set to {@code LEARNING_SYNC}.
 *
 * Fields:
 * - type: Indicates the type of client response, which is fixed to {@code LEARNING_SYNC}.
 * - nextStep: Represents the next step to be executed in the learning process.
 * - startRunning: A boolean value indicating whether the learning process should begin or continue running.
 * - learningRequestId: The unique identifier of the learning request being synchronized.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningClientSyncRequestDTO {
    private FedDBClientResponseType type;
    private Integer nextStep;
    private String currentNodeId;
    private boolean startRunning;
    private String globalUniqueLearningExperimentId;
    private FederatedLearningRelayInfoDTO relayInfo;
    // Target clinic for a run request: the relay info is clinic-specific, so the request is broadcast
    // to the whole experiment and each client applies only the one addressed to its own clinic id.
    private String uniqueRandomClinicId;

    public static LearningClientSyncRequestDTO createNextRequest(Integer nextStep, String requestId) {
        return LearningClientSyncRequestDTO.createRequest(nextStep, null, requestId, false);
    }

    public static LearningClientSyncRequestDTO createRunRequest(Integer currentStep, String requestId) {
        return LearningClientSyncRequestDTO.createRequest(currentStep, null, requestId, true);
    }

    public static LearningClientSyncRequestDTO createNextRequest(Integer nextStep, String currentNodeId, String requestId) {
        return LearningClientSyncRequestDTO.createRequest(nextStep, currentNodeId, requestId, false);
    }

    public static LearningClientSyncRequestDTO createRunRequest(Integer currentStep, String currentNodeId, String requestId) {
        return LearningClientSyncRequestDTO.createRequest(currentStep, currentNodeId, requestId, true);
    }

    private static LearningClientSyncRequestDTO createRequest(Integer nextStep,
                                                              String currentNodeId,
                                                              String requestId,
                                                              boolean startRunning) {
        LearningClientSyncRequestDTO dto = new LearningClientSyncRequestDTO();
        dto.setType(FedDBClientResponseType.LEARNING_SYNC);
        dto.setNextStep(nextStep);
        dto.setCurrentNodeId(currentNodeId);
        dto.setStartRunning(startRunning);
        dto.setGlobalUniqueLearningExperimentId(requestId);
        return dto;
    }
}
