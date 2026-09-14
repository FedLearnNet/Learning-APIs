package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientToolRunOutputDTO {
    private String key;
    private String fileName;
    private ToolConfigDataType type;
    private Long size;
}
