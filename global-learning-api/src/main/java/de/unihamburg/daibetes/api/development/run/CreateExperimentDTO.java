package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.config.ExecutionDiagramConfigDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.ParamDomainDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.SearchStrategyType;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // A grid over the values of each parameter; one without values is left out, the app uses its default.
    @JsonIgnore
    public HyperParamSearchDTO getSearch() {
        HyperParamSearchDTO search = new HyperParamSearchDTO();
        search.setStrategy(SearchStrategyType.GRID);
        if (hyperParams != null) {
            hyperParams.forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    search.getSpace().put(name, ParamDomainDTO.choice(values));
                }
            });
        }
        return search;
    }
}
