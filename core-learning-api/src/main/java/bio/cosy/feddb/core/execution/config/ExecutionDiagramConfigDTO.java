package bio.cosy.feddb.core.execution.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExecutionDiagramConfigDTO {

    @NotBlank
    private String name;

    private String dataAggregatorType;

    @NotBlank
    private String xAxisHeader;

    @NotBlank
    private String yAxisHeader;

    @NotNull
    private String seriesType;
}
