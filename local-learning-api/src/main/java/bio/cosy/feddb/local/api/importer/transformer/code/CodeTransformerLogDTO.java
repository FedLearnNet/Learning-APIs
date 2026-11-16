package bio.cosy.feddb.local.api.importer.transformer.code;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeTransformerLogDTO {
    private Instant time;
    private String message;
}
