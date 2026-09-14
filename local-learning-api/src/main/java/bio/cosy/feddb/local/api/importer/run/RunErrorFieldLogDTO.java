package bio.cosy.feddb.local.api.importer.run;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RunErrorFieldLogDTO {
    Long schemaNodeId; // The schema node name
    String message; // The error message
}
