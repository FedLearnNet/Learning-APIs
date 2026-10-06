package bio.cosy.feddb.local.api.importer.transformer.code.runner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeTransformerRunnerDTO {
    private CodeTransformerRunnerStatus status;
    private String image;
    private String error;
}
