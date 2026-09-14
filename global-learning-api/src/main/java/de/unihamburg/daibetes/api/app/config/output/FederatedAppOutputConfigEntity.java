
package de.unihamburg.daibetes.api.app.config.output;

import de.unihamburg.daibetes.api.app.config.ToolConfigBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_app_output_configs")
public class FederatedAppOutputConfigEntity extends ToolConfigBaseEntity {

    // No additional fields for now
    // Inherits all necessary fields from ToolConfigBaseEntity
}
