package bio.cosy.feddb.core.api.socket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FedDBClientDataDTO<T> {
    private FedDBClientTypeEnum messageType;
    private T message;
}
