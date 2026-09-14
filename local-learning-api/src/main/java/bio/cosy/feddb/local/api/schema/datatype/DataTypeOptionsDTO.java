package bio.cosy.feddb.local.api.schema.datatype;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/*
 * Used to represent a possible value for a data type. The label is the display name
 * and the value is the actual value that will be used in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode()
public class DataTypeOptionsDTO {
    @NotNull
    private String label;
    @NotNull
    private Object value;
    // Non null or Lombok doesn't create the correct constructor

    public DataTypeOptionsDTO(String label) {
        this.label = label;
        this.value = label;
    }
}
