package bio.cosy.feddb.core.api.pipeline;

import lombok.Data;

@Data
public class SastSummaryDTO {
    private int error;
    private int warning;
    private int info;
    private int unknown;
}
