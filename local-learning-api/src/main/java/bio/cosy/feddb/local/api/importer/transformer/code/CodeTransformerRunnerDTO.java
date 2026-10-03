package bio.cosy.feddb.local.api.importer.transformer.code;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeTransformerRunnerDTO {

    public enum Status { STOPPED, STARTING, RUNNING, FAILED }

    private Status status;
    private String image;
    private String error;
}
