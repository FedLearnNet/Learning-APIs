package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedAppTagDTO extends BaseDTO {

    private String name;

    private boolean privacy;

}
