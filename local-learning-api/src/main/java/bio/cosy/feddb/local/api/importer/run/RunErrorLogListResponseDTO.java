package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunErrorLogListResponseDTO {
    private List<ConnectorRunPatientLogDTO> logs = new ArrayList<>();
}
