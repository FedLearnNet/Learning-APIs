package bio.cosy.feddb.local.api.importer.transformer.code;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CodeTransformerSaveDTO extends CodeTransformerCheckDTO {
    private Long id;
    @NotBlank
    private String name;
    private String description = "";
    private String baseImage;
}
