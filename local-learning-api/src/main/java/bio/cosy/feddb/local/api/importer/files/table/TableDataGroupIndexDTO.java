package bio.cosy.feddb.local.api.importer.files.table;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Row locations for one patient group.
 *
 * <p>The row lists use fastutil primitive collections rather than {@code List<Long>} /
 * {@code List<Integer>}: a group carries three entries per row, and boxing them allocated three
 * objects per row for as long as the group was held.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableDataGroupIndexDTO {
    private String patientId;
    @JsonProperty("row_idx")
    private LongArrayList rowIdx;
    @JsonProperty("row_offsets")
    private LongArrayList rowOffsets;
    @JsonProperty("row_lengths")
    private IntArrayList rowLengths;
}
