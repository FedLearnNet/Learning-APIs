package bio.cosy.feddb.local.api.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class LocalSchemaNodeNestedDTO extends LocalSchemaNodeDTO {

    private Set<LocalSchemaNodeNestedDTO> childNodes;
}
