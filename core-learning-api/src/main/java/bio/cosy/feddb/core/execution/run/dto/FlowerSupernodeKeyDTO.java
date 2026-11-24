package bio.cosy.feddb.core.execution.run.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A clinic SuperNode's OpenSSH EC public key on its way to the platform's Flower aggregator app, which
 * registers it with its SuperLink ({@code flwr supernode register}) so the SuperNode may connect. Sent by the
 * clinic's app to local and by global to the aggregator's app; the app-side counterpart is pyfedappwrap's
 * {@code FlowerSupernodeKeyDTO}. The clinic re-sends it under the same request id until it gets an answer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowerSupernodeKeyDTO {
    private String requestId;
    private String publicKey;
    // Set by global to the clinic's participant id, shown by `flwr supernode list`.
    private String name;
}
