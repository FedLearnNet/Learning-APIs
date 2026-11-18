package bio.cosy.feddb.core.api.tre;

import bio.cosy.feddb.core.api.file.FileDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TreSourceDTO {
    private String id;
    private String name;
    private String description;
    private ExternalDatabaseType type;
    private ExternalDatabaseAccess access;

    private Long toolVersionId;
    private String toolName;

    private LinkedHashMap<String, Object> hyperParams;
    private String testData;
    private FileDTO testDataFile;
}
