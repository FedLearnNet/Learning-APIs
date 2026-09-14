package bio.cosy.feddb.core.api.socket;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataStatisticsDTO {
    private List<ColumnProfile> properties;
}
