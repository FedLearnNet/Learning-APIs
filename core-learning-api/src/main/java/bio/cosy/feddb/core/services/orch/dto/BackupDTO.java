package bio.cosy.feddb.core.services.orch.dto;

import lombok.Data;

import java.util.Date;

/**
 * A backup of this deployment as orch-api reports it.
 */
@Data
public class BackupDTO {
    private String name;
    private Date createdAt;
    private String version;
    // with configuration and secrets, else only the databases
    private boolean backUpAll;
    // false while it runs, or when it failed (message) or was interrupted
    private boolean complete;
    private String message;
    // where it is on the server
    private String path;
}
