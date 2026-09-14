package de.unihamburg.daibetes.api.runs.experiment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentDiagramConfigDTO {

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
