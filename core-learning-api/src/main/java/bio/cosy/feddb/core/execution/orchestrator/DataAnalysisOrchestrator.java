package bio.cosy.feddb.core.execution.orchestrator;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.services.orch.dto.StartAppDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.List;


/**
 * ModelOrchestrator is an abstract class that provides methods to orchestrate
 * model execution and workflow cleanup within a containerized environment.
 * It extends the BaseOrchestrator and provides implementations for executing
 * models and managing resources associated with workflows.
 * This class leverages container and volume services to initialize workflows,
 * execute models, and perform cleanup operations.
 * It can be implemented in global or local-learning api
 */
@ApplicationScoped
public class DataAnalysisOrchestrator {

    private final OrchestratorGateway gateway;

    @Inject
    public DataAnalysisOrchestrator(OrchestratorGateway gateway) {
        this.gateway = gateway;
    }


    /**
     * Executes a createPrediction in a container with specified parameters.
     *
     * @param createPrediction The createPrediction creation prediction data transfer object
     * @param id               The workflow ID
     * @param image            The container image to be used
     * @param apiKey           The run-scoped API key
     * @return The container ID of the started container
     * @throws IllegalArgumentException if the input parameters are invalid
     * @throws IllegalStateException    if the container fails to start
     */
    public String execute(DataAnalysisCreatePredictionDTO createPrediction, Long id, String image, String apiKey) throws IllegalArgumentException {
        Long identifier = createPrediction.getModelSubId();
        if (identifier == null) {
            identifier = createPrediction.getAppVersionId();
        }
        StartAppDTO dto = new StartAppDTO();
        dto.setGroupId(id);
        dto.setIdentifier(identifier);
        dto.setAppImage(image);
        dto.setEnvironments(List.of(
                "APP_ID=" + id,
                "APP_API_KEY=" + apiKey
        ));
        // changeUrl is set to true to allow the container to access the app server
        // Path is set to "createPrediction/run" to allow the container to access the app server
        // The serverPort is set to the port of the app server
        String path = DataAnalysisRunModesEnum.PREDICTION.socketPath();
        return gateway.startApplication(dto, path);
    }

    /**
     * Executes a model in a container with specified parameters and uploads a file to the container volume.
     *
     * @param model      The model creation prediction data transfer object
     * @param id         The workflow ID
     * @param image      The container image to be used
     * @param apiKey     The run-scoped API key
     * @param uploadFile The path to the file to be uploaded
     * @return The container ID of the started container
     * @throws IllegalArgumentException if the input parameters are invalid
     */
    public String execute(DataAnalysisCreatePredictionDTO model, Long id, String image, String apiKey,
                          Path uploadFile) throws IllegalArgumentException {

        String containerId = execute(model, id, image, apiKey);
        if (uploadFile != null) {
            gateway.uploadFile(id,
                    model.getModelSubId(),
                    uploadFile);
        }
        return containerId;
    }

    public void cleanup(String containerId) {
        gateway.cleanup(containerId, true);
    }
}
