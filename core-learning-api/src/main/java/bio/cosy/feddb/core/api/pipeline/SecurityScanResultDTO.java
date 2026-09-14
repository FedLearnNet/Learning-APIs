package bio.cosy.feddb.core.api.pipeline;

import lombok.Data;

import java.util.Map;

@Data
public class SecurityScanResultDTO {

    private String tool;

    private String target;

    private boolean success;

    private VulnerabilitySummaryDTO summary;

    private Map<String, Object> rawReport;
}
