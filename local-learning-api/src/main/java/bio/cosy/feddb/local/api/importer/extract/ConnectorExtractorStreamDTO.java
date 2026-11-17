package bio.cosy.feddb.local.api.importer.extract;


import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConnectorExtractorStreamDTO extends ExecutionRunDTO {
    private List<ConnectorFileUploadInfoDTO> uploadInfo;

    private Boolean cached;
    private String hash;
}