package bio.cosy.feddb.local.api.importer.transformer.code.save;

import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CodeTransformerSaveResultDTO extends CodeTransformerCheckResultDTO {
    private Map<String, String> files = new HashMap<>();

    public static CodeTransformerSaveResultDTO failed(String requestId, String message) {
        CodeTransformerSaveResultDTO result = new CodeTransformerSaveResultDTO();
        result.setRequestId(requestId);
        result.setIsSuccess(false);
        result.setMessage(message);
        return result;
    }
}
