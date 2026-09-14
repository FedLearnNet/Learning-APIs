package bio.cosy.feddb.local.api.importer.validation;

import lombok.Data;

@Data
public class PreviewValidationRequestElementDTO {
    private Long schemaId;
    private String mapping;
}
