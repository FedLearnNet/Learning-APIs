package bio.cosy.feddb.core.api.run.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties({"message"})
public class RunMessageMetricDTO extends RunMessageDTO {

    private String metric;
    private String value;

    private String x;
    @JsonProperty("xUnit")
    private String xUnit;

}
