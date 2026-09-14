package bio.cosy.feddb.core.api.file.analytics;


import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Nullable
public class FileProfile{
    private String fileName;
    private long rowsScanned;
    private List<ColumnProfile> columns;
    private List<String> sampleRows;
}
