package bio.cosy.feddb.core.api.model.prediction;

import bio.cosy.feddb.core.base.BaseOrchestrator;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartAppDTO;
import bio.cosy.feddb.core.services.orch.dto.VolumeUploadForm;

import java.io.File;
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
public abstract class DataAnalysisOrchestrator extends BaseOrchestrator {

    public static final String WS_WORKFLOW_PATH = "model/run/{mode}";

    public static String getUrlForRunning(DataAnalysisRunModesEnum mode) {
        return WS_WORKFLOW_PATH
                .replace("{mode}", mode.name());
    }

    /**
     * Executes a model in a container with specified parameters.
     *
     * @param model The model creation prediction data transfer object
     * @param id    The workflow ID
     * @param image The container image to be used
     * @param apiKey The run-scoped API key
     * @return The container ID of the started container
     * @throws IllegalArgumentException if the input parameters are invalid
     * @throws IllegalStateException    if the container fails to start
     */
    public String execute(DataAnalysisCreatePredictionDTO model, Long id, String image, String apiKey) throws IllegalArgumentException {
        Long identifier = model.getModelSubId();
        if (identifier == null) {
            identifier = model.getAppVersionId();
        }
        StartAppDTO dto = new StartAppDTO();
        dto.setGroupId(id);
        dto.setIdentifier(identifier);
        dto.setAppImage(image);
        dto.setEnvironments(List.of(
                "APP_ID=" + id,
                "APP_API_KEY=" + apiKey
        ));
        // changeUrl is set to true to allow the container to access the ModelAppServer
        // Path is set to "model/run" to allow the container to access the ModelAppServer
        // The serverPort is set to the port of the ModelAppServer
        String path = getUrlForRunning(DataAnalysisRunModesEnum.PREDICTION);
        CreateContainerResponseDTO resp = getContainerClient().startContainer(dto, true, true, path, serverPort);
        String containerId = resp.getId();
        if (containerId == null) {
            throw new IllegalStateException("Failed to start container for model " + image);
        }
        return containerId;
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
            File file = uploadFile.toFile();
            VolumeUploadForm form = new VolumeUploadForm(file, file.getName());
            getVolumeClient().uploadFilesIds(id, model.getModelSubId(), form);
        }
        return containerId;
    }

}
