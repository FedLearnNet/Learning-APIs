package bio.cosy.feddb.core.api.datamodler.validation;

import lombok.Data;

@Data
public class ValidationResultDTO {
    private boolean valid;
    private String message;

    public ValidationResultDTO() {
        this.valid = false;
    }


}
