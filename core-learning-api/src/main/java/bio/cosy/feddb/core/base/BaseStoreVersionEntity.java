package bio.cosy.feddb.core.base;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public abstract class BaseStoreVersionEntity extends BaseEntity {

    @Column(name = "major_version")
    private Long majorVersion;

    @Column(name = "minor_version")
    private Long minorVersion;

    @Column(name = "patch_version")
    private Long patchVersion;

    @Column(name = "changelog")
    private String changelog;


    public BaseStoreVersionEntity() {
    }

    public String toString() {
        String var10000 = this.getClass().getSimpleName();
        return var10000 + "<" + this.getId() + ">";
    }

    public String getVersionFormatted() {
        return String.format("%d.%d.%d",
                this.getMajorVersion(),
                this.getMinorVersion(),
                this.getPatchVersion());
    }

    private Long parseVersion(String version, int index) {
        String[] parts = version.split("\\.");
        return Long.parseLong(parts[index]);
    }

    public void setMajorVersion(String versionString) {
        this.majorVersion = parseVersion(versionString, 0);
    }

    public void setMinorVersion(String versionString) {
        this.minorVersion = parseVersion(versionString, 1);
    }

    public void setPatchVersion(String versionString) {
        this.patchVersion = parseVersion(versionString, 2);
    }

    public void increasePatchVersion() {
        this.patchVersion++;
    }

}

