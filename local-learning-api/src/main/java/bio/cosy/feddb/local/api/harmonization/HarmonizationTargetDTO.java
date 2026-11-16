package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarmonizationTargetDTO {
    private String name;
    private DataTypeDTO dataType;
    private boolean staticColumn;
}
