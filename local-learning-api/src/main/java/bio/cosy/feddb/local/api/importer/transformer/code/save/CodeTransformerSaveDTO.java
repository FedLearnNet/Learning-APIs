package bio.cosy.feddb.local.api.importer.transformer.code.save;

import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
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
