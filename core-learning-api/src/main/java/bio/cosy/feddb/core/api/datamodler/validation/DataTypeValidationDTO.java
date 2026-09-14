package bio.cosy.feddb.core.api.datamodler.validation;

import lombok.Data;

@Data
public class DataTypeValidationDTO {
    private DataTypeValidationType name;
    private String validator;
    private String message;
}
