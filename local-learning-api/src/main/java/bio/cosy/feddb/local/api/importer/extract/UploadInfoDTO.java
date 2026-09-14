package bio.cosy.feddb.local.api.importer.extract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadInfoDTO {
    private List<String> renamedColumns;
    private List<Boolean> deletedColumns;
    private List<String> columns;
    private String lastUploaded;
}
