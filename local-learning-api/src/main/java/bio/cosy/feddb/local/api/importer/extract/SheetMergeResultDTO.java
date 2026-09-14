package bio.cosy.feddb.local.api.importer.extract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SheetMergeResultDTO {
    private String uidColumn;
    private Map<String, String> sheetUidMapping;
    private String commonUidColumnName;
}
