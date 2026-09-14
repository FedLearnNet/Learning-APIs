package bio.cosy.feddb.local.api.importer.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewValidationWarningDTO {
    private PreviewValidationWarningType type;
    private String message;
}
