package bio.cosy.feddb.local.api.harmonization;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarmonizationColumnMeaningDTO {

    private String column;
    private String description;
}
