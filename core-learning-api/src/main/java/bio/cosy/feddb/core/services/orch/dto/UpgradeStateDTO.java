package bio.cosy.feddb.core.services.orch.dto;

import lombok.Data;

import java.util.Date;

@Data
public class UpgradeStateDTO {
    private MaintenanceMode step;
    private boolean done;
    // FL-Net version that is prepared or applied
    private String flNetSoftwareVersion;
    private String lastLogMessage;
    private Date updatedAt;
}
