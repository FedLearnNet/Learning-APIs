package bio.cosy.feddb.local.api.harmonization;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarmonizationColumnMeaningsDTO {

    private List<HarmonizationColumnMeaningDTO> columns;
    private List<List<String>> sameKind;
}
