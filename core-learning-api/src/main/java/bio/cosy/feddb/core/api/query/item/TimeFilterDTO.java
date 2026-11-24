package bio.cosy.feddb.core.api.query.item;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// both exclusive, JSON as YYYY-MM-DD; no filter means anytime
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeFilterDTO {
    private LocalDate after;
    private LocalDate before;
}
