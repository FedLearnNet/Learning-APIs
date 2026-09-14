package bio.cosy.feddb.core.base;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class BaseAuthDTO extends BaseDTO {

    String keycloakId;
}
