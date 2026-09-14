package de.unihamburg.daibetes.api.workflow.export;

import bio.cosy.feddb.core.api.workflow.node.WorkflowPositionDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.UUID;

@Data
public class WorkflowNodeExportDTO {
    @NotBlank(message = "NodeId needs to bet set")
    private String nodeId;

    private UUID appUniqueId;
    private String versionPublishHash;

    private String modelSubPublishHash;

    private WorkflowPositionDTO position;

    private LinkedHashMap<String, Object> hyperParams;

    private boolean hasChildren = false;
    private boolean hasParent = false;

    //will be calculated on create/update
    private Integer executionOrder;
}
