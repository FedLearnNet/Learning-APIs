package bio.cosy.feddb.local.api.schema;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class LocalSchemaRootNodeDTO extends BaseDTO {
    @NotBlank
    @JsonProperty("nodeType")
    private SchemaNodeType type = SchemaNodeType.ROOT;

    private String globalId;
    private Long globalVersion;
    private String name;
    private String description;

    private String createdBy;

    private Set<LocalSchemaNodeNestedDTO> childNodes;

    @Override
    public String toString() {
        return "Root Schema{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", globalVersion=" + globalVersion +
                ", globalId='" + globalId + '\'' +
                ", type=" + type +
                '}';
    }
}
