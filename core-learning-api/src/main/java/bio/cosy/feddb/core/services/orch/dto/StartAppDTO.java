package bio.cosy.feddb.core.services.orch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


/**
 * Data Transfer Object representing the information required to start an application
 * within a specific workflow context for the Orch API. This class contains details about the application,
 * workflow steps, and related settings necessary for its initialization.
 * <p>
 * Fields:
 * - appId: Identifier for the application to be started. Must not be null.
 * - appImage: Image identifier of the application. Must not be blank.
 * - workflowId: Identifier for the associated workflow. Must not be null.
 * - workflowStep: Current step in the workflow. Must not be null and must be greater than 0.
 * - workflowMaxSteps: Total number of steps in the workflow. Must not be null and must be greater than 0.
 * - environments: List of environment settings for the application. Default value is an empty list.
 * - needsVolume: Boolean indicating whether the application requires volume setup.
 * - hardCleanup: Boolean indicating whether a hard cleanup is necessary after execution.
 */
@Data
public class StartAppDTO {

    @NotNull(message = "identifier cannot be null")
    private Long identifier;

    @NotBlank(message = "appImage cannot be blank")
    private String appImage;

    @NotNull(message = "groupId cannot be null")
    private Long groupId;

    // Its a key=value pair like "APP_ID=1"
    private List<String> environments = new ArrayList<>();

    private Boolean needsInternetAccess = true; // default true;
    private Boolean needsHostAccess = false;

}
