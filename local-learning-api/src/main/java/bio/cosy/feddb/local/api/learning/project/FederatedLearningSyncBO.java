package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.LearningClientStopRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.api.socket.StartLearningClientRequestDTO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentAO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.util.Optional;

@ApplicationScoped
public class FederatedLearningSyncBO {

    @Inject
    FederatedLearningExperimentAO experimentAO;

    @Inject
    FederatedLearningExperimentBO experimentBO;

    @Inject
    FederatedLearningProjectBO federatedLearningProjectBO;

    @Inject
    FederatedLearningRequestBO requestBO;

    public Optional<LearningClientSyncResponseDTO> handleStartLearningRequest(StartLearningClientRequestDTO requestLearning) {
        Log.infof("Received start learning request: %s", requestLearning);

        Optional<FederatedLearningExperimentEntity> experimentOptional = experimentAO.getByGlobalRequestId(requestLearning.getGlobalUniqueLearningExperimentId());
        Optional<String> randomClinicIdOptional = experimentOptional.map(FederatedLearningExperimentEntity::getUniqueRandomClinicId);

        if (randomClinicIdOptional.isEmpty()) {
            Log.errorf("Experiment not found for learning request ID: %s", requestLearning.getGlobalUniqueLearningExperimentId());
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Experiment not found", requestLearning.getGlobalUniqueLearningExperimentId()));
        }
        FederatedLearningExperimentEntity experiment = experimentOptional.orElse(null);
        String uniqueRandomClinicId = randomClinicIdOptional.get();

        if (requestLearning.getCoordinatorId() != null && requestLearning.getCoordinatorId().equals(uniqueRandomClinicId)) {
            federatedLearningProjectBO.setProjectAsCoordinatedTransactional(experiment.getProject().getId());
        }
        if (Boolean.FALSE.equals(requestLearning.getModelCanBePublic())) {
            requestBO.applyModelCanBePublicVeto(requestLearning.getGlobalUniqueLearningExperimentId());
        }
        // Try to find the project associated with the learning request ID
        Optional<ProjectDetailDTO> projectOptional = federatedLearningProjectBO.findByGlobalUniqueExperimentIdOptionalTransactional(requestLearning.getGlobalUniqueLearningExperimentId());

        // Return error if project not found
        if (projectOptional.isEmpty()) {
            Log.warnf("Stop processing start learning request: %s, learning request not found", requestLearning);
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Learning request not found", requestLearning.getGlobalUniqueLearningExperimentId(), uniqueRandomClinicId));
        }

        ProjectStatus status = handleStartLearningRequest(experiment.getId());
        Log.infof("Successfully started learning workflow for request ID: %s", requestLearning.getGlobalUniqueLearningExperimentId());
        String currentNodeId = findCurrentNodeId(experiment.getId());
        LearningClientSyncResponseDTO response = LearningClientSyncResponseDTO.createResponse(
                requestLearning.getGlobalUniqueLearningExperimentId(),
                uniqueRandomClinicId,
                currentNodeId
        );
        response.setStepStatus(status);
        return Optional.of(response);
    }

    public ProjectStatus handleStartLearningRequest(Long experimentId) {
        return experimentBO.startLearning(experimentId, true, null);
    }

    /**
     * Handles the next step in the federated learning workflow based on the client sync request.
     *
     * @param requestLearning The sync request containing learning parameters and step information
     * @return Optional containing either a success response, error response, or empty if just starting execution
     */
    @Retry(maxRetries = 8, delay = 75, jitter = 25)
    public Optional<LearningClientSyncResponseDTO> handleNextStep(LearningClientSyncRequestDTO requestLearning) {
        Log.infof("Processing next step request: %s", requestLearning);

        Optional<FederatedLearningExperimentEntity> experiment = experimentAO.getByGlobalRequestId(requestLearning.getGlobalUniqueLearningExperimentId());
        Optional<String> randomClinicIdOptional = experiment.map(FederatedLearningExperimentEntity::getUniqueRandomClinicId);

        if (randomClinicIdOptional.isEmpty()) {
            Log.errorf("Experiment not found for learning request ID: %s", requestLearning.getGlobalUniqueLearningExperimentId());
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Experiment not found", requestLearning.getGlobalUniqueLearningExperimentId()));
        }

        String uniqueRandomClinicId = randomClinicIdOptional.get();

        // Try to find project associated with the learning request ID
        Optional<ProjectDetailDTO> projectOptional = federatedLearningProjectBO.findByGlobalUniqueExperimentIdOptionalTransactional(requestLearning.getGlobalUniqueLearningExperimentId());

        if (projectOptional.isEmpty()) {
            Log.warnf("Stop processing next step request: %s, learning request not found", requestLearning);
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Learning request not found", requestLearning.getGlobalUniqueLearningExperimentId()));
        }

        FederatedLearningExperimentEntity experimentEntity = experiment.get();
        String requestedNodeId = requestLearning.getCurrentNodeId();

        // Check if this is a start running request or next step request
        // This starts the whole workflow.
        // As START_LEARNING is used only for the request and not for the actual start of a learning
        // the learning start is handled here!
        if (requestLearning.isStartRunning()) {
            // Run requests carry clinic-specific relay info and are broadcast to the whole experiment.
            // Only act on the one addressed to this clinic; ignore the others.
            String targetClinicId = requestLearning.getUniqueRandomClinicId();
            if (targetClinicId != null && !targetClinicId.equals(uniqueRandomClinicId)) {
                Log.debugf("Ignoring run request for clinic %s (this clinic is %s) for learning request ID: %s",
                        targetClinicId, uniqueRandomClinicId, requestLearning.getGlobalUniqueLearningExperimentId());
                return Optional.empty();
            }
            String currentNodeId = findCurrentNodeId(experimentEntity.getId());
            if (requestedNodeId != null && currentNodeId != null && !requestedNodeId.equals(currentNodeId)) {
                Log.warnf("Ignoring out-of-sync start request for learning request ID: %s. Expected current node %s but received %s",
                        requestLearning.getGlobalUniqueLearningExperimentId(), currentNodeId, requestedNodeId);
                return Optional.empty();
            }
            if (isCurrentStepAlreadyRunning(experimentEntity)) {
                Log.infof("Ignoring duplicate start request for learning request ID: %s on node %s",
                        requestLearning.getGlobalUniqueLearningExperimentId(), currentNodeId);
                return Optional.empty();
            }
            Log.infof("Starting execution step for learning request ID: %s", requestLearning.getGlobalUniqueLearningExperimentId());
            try {
                experimentBO.handleStartLearning(experimentEntity.getId(), requestLearning.getRelayInfo());
                return Optional.empty();
            } catch (Exception e) {
                Log.error("Error starting learning for learning ID " + requestLearning.getGlobalUniqueLearningExperimentId(), e);
                return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Error starting learning: " + e.getMessage(), requestLearning.getGlobalUniqueLearningExperimentId(), uniqueRandomClinicId));
            }
        } else {
            // Execute next workflow step
            Log.infof("Executing next workflow step %d for learning request ID: %s",
                    requestLearning.getNextStep(), requestLearning.getGlobalUniqueLearningExperimentId());
            if (requestedNodeId != null) {
                Log.infof("Global requested next workflow node %s for learning request ID: %s",
                        requestedNodeId, requestLearning.getGlobalUniqueLearningExperimentId());
            }
            experimentBO.startLearning(experimentEntity.getId(), false, requestLearning.getRelayInfo());
        }

        Log.infof("Successfully processed next step for learning request ID: %s", requestLearning.getGlobalUniqueLearningExperimentId());
        return Optional.empty();

    }


    /**
     * Stops a federated learning workflow and cleans up associated resources.
     *
     * @param stopLearning The stop request containing the learning request ID to terminate
     */
    @Transactional
    public void stopLearning(LearningClientStopRequestDTO stopLearning) {
        Log.infof("Received stop learning request for ID: %s", stopLearning.getGlobalUniqueLearningExperimentId());

        // Try to find the associated project
        Optional<FederatedLearningExperimentEntity> experiment = experimentAO.getByGlobalRequestId(stopLearning.getGlobalUniqueLearningExperimentId());
        if (experiment.isEmpty()) {
            Log.warnf("Stop processing start learning request: %s, learning request not found", stopLearning);
            return;
        }

        // Stop the learning process
        Log.infof("Stopping learning process for request ID: %s", stopLearning.getGlobalUniqueLearningExperimentId());
        experimentBO.stopLearning(experiment.get().getId());
    }

    private String findCurrentNodeId(Long experimentId) {
        FederatedLearningExperimentEntity updatedExperiment = experimentAO.findById(experimentId);
        if (updatedExperiment == null || updatedExperiment.getCurrentWorkflowNode() == null || updatedExperiment.getCurrentWorkflowNode().getWorkflowNode() == null) {
            return null;
        }
        return updatedExperiment.getCurrentWorkflowNode().getWorkflowNode().getNodeId();
    }

    private boolean isCurrentStepAlreadyRunning(FederatedLearningExperimentEntity experiment) {
        if (experiment.getCurrentWorkflowNode() == null) {
            return false;
        }
        RunStatusTypes currentStatus = experiment.getCurrentWorkflowNode().getStepStatus();
        return currentStatus == RunStatusTypes.RUNNING || RunStatusTypes.isFinalStatus(currentStatus);
    }
}
