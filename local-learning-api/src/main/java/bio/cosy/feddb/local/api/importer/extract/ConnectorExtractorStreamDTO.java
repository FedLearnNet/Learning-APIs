package bio.cosy.feddb.local.api.importer.extract;


import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConnectorExtractorStreamDTO extends ExecutionRunDTO {
    private List<ConnectorFileUploadInfoDTO> uploadInfo;

    /** Stored extractor output name → connector file id, for later preview/validation. */
    private LinkedHashMap<String, Object> outputParams;

    private Boolean cached;
    private String hash;
}