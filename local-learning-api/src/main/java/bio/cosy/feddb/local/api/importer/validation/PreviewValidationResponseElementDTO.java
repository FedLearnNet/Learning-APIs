package bio.cosy.feddb.local.api.importer.validation;

import lombok.Data;

@Data
public class PreviewValidationResponseElementDTO {
    private String value;
    private boolean validated;
    private boolean mapped;
    private ConnectorValidationResultDTO result;
}
