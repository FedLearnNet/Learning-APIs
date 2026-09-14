package de.unihamburg.daibetes.api.runs.test.federated;

import lombok.Data;

@Data
public class FederatedTestRunConfigDTO {
    private Double pollInterval;
    private Double timeout;
    private Integer maxPolls;

    private Boolean useContainerizedController;
}
