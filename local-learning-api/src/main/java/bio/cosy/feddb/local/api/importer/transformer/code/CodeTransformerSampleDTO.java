package bio.cosy.feddb.local.api.importer.transformer.code;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeTransformerSampleDTO {
    private Object input;
    private Object output;
    private String error;
}
