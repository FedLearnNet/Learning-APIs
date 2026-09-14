package bio.cosy.feddb.core.api.datamodler.datatype;

import lombok.Data;

@Data
public class DataTypeUsagesDTO {
    private DataTypeNodeDTO dataType;
    private Boolean usage;
}
