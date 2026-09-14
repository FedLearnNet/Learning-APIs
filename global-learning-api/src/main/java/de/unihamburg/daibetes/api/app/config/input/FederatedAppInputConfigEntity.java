
package de.unihamburg.daibetes.api.app.config.input;

import de.unihamburg.daibetes.api.app.config.ToolConfigBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_app_input_configs")
public class FederatedAppInputConfigEntity extends ToolConfigBaseEntity {

    // No additional fields for now
    // Inherits all necessary fields from ToolConfigBaseEntity
    private boolean required;
}
