package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class SimpleLocalSchemaNodeDTO extends BaseDTO {
    @NotBlank
    @JsonProperty("nodeType")
    private SchemaNodeType type;

    @NotBlank
    private String name;
    private String description;

    @NotBlank
    private String globalId;
    private Long parentId;

}
