package bio.cosy.feddb.local.api.importer.extract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class PivotConfigDTO {
    private Map<String, Integer> valueColumnIndex = new LinkedHashMap<>();
    private Map<String, PivotMode> mode = new LinkedHashMap<>();
    private Map<String, String> prefix = new LinkedHashMap<>();
    private Map<String, PivotValueFormat> valueFormat = new LinkedHashMap<>();

    public PivotConfigDTO(Map<String, Integer> valueColumnIndex) {
        this.valueColumnIndex = valueColumnIndex;
    }
}
