package bio.cosy.feddb.local.api.importer.extract;


import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConnectorExtractorStreamDTO extends ConnectorRunStepDTO {
    private List<ConnectorFileUploadInfoDTO> uploadInfo;

    private Boolean cached;
    private String hash;
}