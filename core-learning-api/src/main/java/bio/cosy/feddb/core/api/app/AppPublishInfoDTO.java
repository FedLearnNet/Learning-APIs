package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.api.pipeline.MalwareScanResultDTO;
import bio.cosy.feddb.core.api.pipeline.SecurityScanResultDTO;
import lombok.Data;

import java.util.List;

@Data
public class AppPublishInfoDTO {
    private String commitHash;
    private List<String> filePaths;
    private SecurityScanResultDTO vulnerabilityScanResult;
    private MalwareScanResultDTO malwareScanResult;
    private String imageName;
}
