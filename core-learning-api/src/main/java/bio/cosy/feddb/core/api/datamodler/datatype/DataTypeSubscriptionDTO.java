package bio.cosy.feddb.core.api.datamodler.datatype;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataTypeSubscriptionDTO {
    private String dataTypeId;
    private Integer subscriptionsCount;
}
