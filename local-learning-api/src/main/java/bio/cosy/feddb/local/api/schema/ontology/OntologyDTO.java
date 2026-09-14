package bio.cosy.feddb.local.api.schema.ontology;

import bio.cosy.feddb.core.base.BaseDTO;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class OntologyDTO extends BaseDTO {
    @NotBlank
    private String name;
    private String description;
    // @JsonProperty("parent_ids")
    // private Set<String> parentIds;
    // @JsonProperty("children_ids")
    // private Set<String> childrenIds;

    @NotBlank
    @JsonAlias({"unique_id", "globalId"})
    private String globalId;
    
    @JsonProperty("root_source")
    private String rootSource;
}
