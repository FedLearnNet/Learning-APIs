package bio.cosy.feddb.local.api.importer.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConnectorValidationResultDTO {
    private String message;
    private Long schemaId;
    private boolean valid;

    public ConnectorValidationResultDTO(String message) {
        this.message = message;
        this.valid = false;
    }

    public ConnectorValidationResultDTO(String message, Long schemaId) {
        this.message = message;
        this.valid = false;
        this.schemaId = schemaId;
    }

    public ConnectorValidationResultDTO(Long schemaId) {
        this.message = "Valid";
        this.schemaId = schemaId;
        this.valid = true;
    }
}
