package bio.cosy.feddb.core.api.pipeline;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SastScanResultDTO {
    private String tool = "opengrep";
    private String version;
    private String target;
    private boolean success;
    private boolean complete;
    private Integer scannedFiles;
    private SastSummaryDTO summary;
    private List<SastFindingDTO> findings = new ArrayList<>();
    private List<SastScanErrorDTO> errors = new ArrayList<>();
    private Map<String, Object> rawReport;
}
