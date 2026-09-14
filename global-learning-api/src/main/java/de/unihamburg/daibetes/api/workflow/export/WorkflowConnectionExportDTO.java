package de.unihamburg.daibetes.api.workflow.export;

import lombok.Data;

@Data
public class WorkflowConnectionExportDTO {
    private String inputId;
    private String outputId;

    private boolean isInputConnection;

    private String inputNodeId;
    private String outputNodeId;

    private String inputFileName;
    private String outputFileName;

    private String inputConfigName;
    private String outputConfigName;
}
