package bio.cosy.feddb.core.api.datamodler.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class SchemaStructureDTO extends SchemaNodeDetailDTO {
    private List<SchemaStructureDTO> children;
}
