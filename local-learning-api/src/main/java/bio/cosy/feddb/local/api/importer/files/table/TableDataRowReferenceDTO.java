package bio.cosy.feddb.local.api.importer.files.table;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableDataRowReferenceDTO {
    private String patientId;
    @JsonProperty("row_idx")
    private long rowIdx;
    @JsonProperty("row_offset")
    private long rowOffset;
    @JsonProperty("row_length")
    private int rowLength;
}
