package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.api.run.message.BaseRunMessageEntity;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "connector_run_step_messages",
        indexes = {
                @Index(name = "idx_connector_run_step_messages_run_step_id", columnList = "run_step_id"),
                @Index(name = "idx_connector_run_step_messages_run_transformer_id", columnList = "run_transformer_id"),
                @Index(name = "idx_connector_run_step_messages_run_id", columnList = "run_id")
        })
public class ConnectorRunMessagesEntity extends BaseRunMessageEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_step_id")
    private ConnectorRunStepEntity step;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_transformer_id", nullable = true)
    private ConnectorTransformerEntity transformer;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_id")
    private ConnectorRunEntity run;

    @Enumerated(EnumType.STRING)
    private ConnectorRunMessageLevels severity;
}
