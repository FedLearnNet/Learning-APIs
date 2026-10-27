package bio.cosy.feddb.local.api.importer.run.message;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A log line of an import; what its apps log goes to the messages of their runs. */
@Setter
@Getter
@Entity
@Table(name = "connector_run_step_messages",
        indexes = {
                @Index(name = "idx_connector_run_step_messages_run_transformer_id", columnList = "run_transformer_id"),
                @Index(name = "idx_connector_run_step_messages_run_id", columnList = "run_id")
        })
public class ConnectorRunMessagesEntity extends BaseEntity {

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private ConnectorRunMessageLevels severity;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_transformer_id")
    private ConnectorTransformerEntity transformer;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "run_id")
    private ConnectorRunEntity run;
}
