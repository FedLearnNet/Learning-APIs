package bio.cosy.feddb.local.api.importer.transformer.code;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeTransformerCheckResultDTO {
    private String requestId;
    private Boolean isSuccess;
    private String message;
    private List<String> inputKeys = new ArrayList<>();
    private List<String> returnKeys = new ArrayList<>();
    private List<CodeTransformerSampleDTO> samples = new ArrayList<>();

    public static CodeTransformerCheckResultDTO failed(String requestId, String message) {
        return new CodeTransformerCheckResultDTO(requestId, false, message, List.of(), List.of(), List.of());
    }
}
