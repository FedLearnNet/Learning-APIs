package bio.cosy.feddb.core.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public abstract class BaseFileEntity extends BaseAuthEntity {

    //nullable is default true, but for clarity set here
    @Column(nullable = true)
    public String fileName;

    @Column(nullable = true)
    public String contentType;

    @Column(nullable = true)
    public Long size;

    @Column(length = 32)
    public String secret;

    /**
     * The OID of the large object stored in pg_largeobject.
     */
    @Column(name = "lo_oid", nullable = true)
    public Long largeObjectId;


    public BaseFileEntity() {
    }

    public String toString() {
        String var10000 = this.getClass().getSimpleName();
        return var10000 + "<" + this.getId() + ">";
    }

}
