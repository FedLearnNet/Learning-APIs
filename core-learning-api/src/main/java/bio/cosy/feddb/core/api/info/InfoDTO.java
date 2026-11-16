package bio.cosy.feddb.core.api.info;

import lombok.Data;

@Data
public class InfoDTO {
    // X.Y.Z.dev, X.Y.Z.rcN or X.Y.Z
    private String version;

    private StartupImportState startupImports;
}
