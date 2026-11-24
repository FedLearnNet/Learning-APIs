package bio.cosy.feddb.core.execution.run.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The platform's answer to a {@link FlowerSupernodeKeyDTO}, matched by its request id: sent by the aggregator's
 * app to global, by global to the clinic's local and by local to the clinic's app. A failed answer is usually
 * "not ready yet" and the clinic simply asks again.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowerSupernodeRegisteredDTO {
    private String requestId;
    private boolean success;
    private String error;

    public static FlowerSupernodeRegisteredDTO refused(String requestId, String error) {
        return new FlowerSupernodeRegisteredDTO(requestId, false, error);
    }
}
