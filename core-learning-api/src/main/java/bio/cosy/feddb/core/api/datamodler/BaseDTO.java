package bio.cosy.feddb.core.api.datamodler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseDTO {
    private UUID id;

    public void setIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
