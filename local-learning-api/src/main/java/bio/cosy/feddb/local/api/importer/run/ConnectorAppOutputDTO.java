package bio.cosy.feddb.local.api.importer.run;

import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorAppOutputDTO {
    private ExecutionRunDTO run;
    private TableData outputData;
    private LinkedHashMap<String, Object> storedOutputs;
}
