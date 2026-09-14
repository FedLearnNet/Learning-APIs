package bio.cosy.feddb.local.api.importer.run;

import java.util.List;
import java.util.ArrayList;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatientErrorLogsDTO {
    private String message;
    private String externalPatientId;
    private boolean updated = false;
        // if not set, no changes were made to this patient
        // updated includes if new data entries were added
    private List<RunErrorFieldLogDTO> errorFields = new ArrayList<>();
}
