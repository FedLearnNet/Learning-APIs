package bio.cosy.feddb.local.api.harmonization;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarmonizationResultDTO {

    private Long outputFileId;
    private int rows;
    // Per target column, how many source values were rejected and left empty
    private Map<String, Integer> rejected;
    // Source keys missing in the reference file, so their static columns come from the source
    private List<String> unmatched;
    private List<Map<String, Object>> preview;
}
