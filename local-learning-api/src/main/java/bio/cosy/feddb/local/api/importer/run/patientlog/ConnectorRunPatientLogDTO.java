package bio.cosy.feddb.local.api.importer.run.patientlog;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectorRunPatientLogDTO extends BaseDTO {

    private String patientId;
    private String field;
    private String message;
    private String level = "ERROR";
    private ConnectorRunPatientLogType logType;
    private Long runId;
}
