package bio.cosy.feddb.local.api.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LocalInfoDTO {
    private String version;

    private String platformVersion;
    private boolean platformVersionNewer;
    // Platform frontend, e.g. for links into the Tool store
    private String globalWebUrl;

    public LocalInfoDTO(String version, String platformVersion) {
        this.version = version;
        this.platformVersion = platformVersion;
    }
}
