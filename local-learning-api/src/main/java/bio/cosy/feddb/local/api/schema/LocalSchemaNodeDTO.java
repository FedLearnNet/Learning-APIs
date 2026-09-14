package bio.cosy.feddb.local.api.schema;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.ontology.OntologyDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class LocalSchemaNodeDTO extends BaseDTO {
    @NotBlank
    @JsonProperty("nodeType")
    private SchemaNodeType type;

    @NotBlank
    private String name;
    private String description;

    private OntologyDTO ontology;
    private DataTypeDTO dataType;

    @NotBlank
    private String globalId;
    private Long parentId;

    private Set<Long> childrenIds;
}
