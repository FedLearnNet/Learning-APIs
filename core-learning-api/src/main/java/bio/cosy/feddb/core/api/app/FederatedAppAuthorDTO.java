package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;

import java.util.Objects;

@Data
public class FederatedAppAuthorDTO extends BaseAuthDTO {

    private Long federatedAppId;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FederatedAppAuthorDTO that = (FederatedAppAuthorDTO) o;
        return Objects.equals(getFederatedAppId(), that.getFederatedAppId()) &&
                Objects.equals(getKeycloakId(), that.getKeycloakId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getFederatedAppId(), getKeycloakId());
    }
}
