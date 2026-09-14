package de.unihamburg.daibetes.api.workflow.export;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.workflow.WorkflowInputDTO;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowExportDTO {
    private List<WorkflowNodeExportDTO> nodes = List.of();
    private List<WorkflowConnectionExportDTO> connections = List.of();
    private List<WorkflowInputDTO> inputs = List.of();

    private String keycloakId;

    private String name;
    private String description;
    private PublishStatus publishStatus;
}
