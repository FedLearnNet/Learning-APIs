package bio.cosy.feddb.core.api.socket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataStatisticsResponseClientDTO {
    private DataStatisticsDTO statistics;
    private String globalQueryId;
    private String randomClinicId;
    private UUID requestId;
}
