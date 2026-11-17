package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.tre.ExternalDatabaseAccess;
import bio.cosy.feddb.core.api.tre.ExternalDatabaseType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

// A database the cohort links instead of a schema, only for TRE runs.
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalDatabaseDTO {
    private ExternalDatabaseType type;
    private ExternalDatabaseLocation location;
    // container name, or host name or IP of an address
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String password;
    private LinkedHashMap<String, String> env;

    private ExternalDatabaseAccess access;
    // the only tool version reading the database with TOOL access
    private Long toolVersionId;
    private String toolName;
    private LinkedHashMap<String, Object> hyperParams;
    // synthetic test data: a script, a link or how to get it, shown on the platform to build tools against
    @Size(max = 262144)
    private String testData;
    // on the platform's file store, set only by uploading it
    private FileDTO testDataFile;
}
