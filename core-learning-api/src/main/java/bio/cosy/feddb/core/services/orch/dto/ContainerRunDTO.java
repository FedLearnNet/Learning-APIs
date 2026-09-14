package bio.cosy.feddb.core.services.orch.dto;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Data Transfer Object (DTO) representing the details of a container run operation.
 * Extends the base DTO providing common fields for identification and metadata.
 * Its purpose is to encapsulate the information related to a container's runtime from the OrchApi
 *
 * Fields:
 * - Represents specific attributes related to a container's runtime,
 *   such as the associated application, workflow, container identifier, and status.
 *
 * This class includes:
 * - Information about the application and its image.
 * - Workflow-related identifiers for tracking progress and steps within the workflow.
 * - Details about the container, including its name, ID, and runtime status.
 *
 * The `status` field relies on the `ContainerRunStatus` enumeration to represent
 * various states of the container's runtime lifecycle.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContainerRunDTO extends BaseDTO {
    private Long appId;

    private String appImage;

    private Long workflowId;

    private Long workflowStep;

    private Long workflowMaxSteps;

    private String containerName;

    private String containerId;

    private ContainerRunStatus status;

}
