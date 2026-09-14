package bio.cosy.feddb.core.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public abstract class BaseAuthEntity extends BaseEntity {

    @Column(name = "keycloak_id", nullable = false)
    private String keycloakId;


    public BaseAuthEntity() {
    }

    public String toString() {
        String var10000 = this.getClass().getSimpleName();
        return var10000 + "<" + this.getId() + ">";
    }

}
