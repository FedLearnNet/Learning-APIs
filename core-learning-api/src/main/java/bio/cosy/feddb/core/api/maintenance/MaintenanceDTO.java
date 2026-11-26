package bio.cosy.feddb.core.api.maintenance;

import bio.cosy.feddb.core.services.orch.dto.MaintenanceStatusDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MaintenanceDTO {
    private String installedVersion;
    // what can be installed, empty if nothing
    private String targetVersion;
    private MaintenanceStatusDTO status;
}
