package de.unihamburg.daibetes.execution.federation;

import lombok.Data;

@Data
public class ExecutionFederationDTO {
    private Integer totalRounds;
    private Integer currentRound;
    private Boolean startAggregator;
    private Double pollInterval;
    private Double timeout;
    private Integer maxPolls;
    private Boolean relayStopped;
}
