package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.config.ExecutionDiagramConfigDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

@Data
public class CreateExperimentDTO {

    @NotBlank(message = "Name is mandatory")
    private String name;

    @NotBlank(message = "Description is mandatory")
    private String description;

    @NotNull(message = "appVersionId is mandatory")
    private Long federatedAppVersionId;

    private String inputData;
    private LinkedHashMap<String, String> inputFilePaths;

    private List<ExecutionDiagramConfigDTO> diagramConfigs;
    private LinkedHashMap<String, List<Object>> hyperParams;

}
