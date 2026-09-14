package bio.cosy.feddb.local.api.importer.run.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "connector_run_steps",
        indexes = {
                @Index(name = "idx_connector_run_steps_connector_run_id", columnList = "connector_run_id"),
                @Index(name = "idx_connector_run_steps_connector_transformation_id", columnList = "connector_transformation_id")
        })
public class ConnectorRunStepEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(length = 2000, name = "last_error")
    private String lastError;

    private Float progress;

    @Column(name = "container_id")
    private String containerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hyper_params", columnDefinition = "jsonb")
    private LinkedHashMap<String, Object> hyperParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_paths", columnDefinition = "jsonb")
    private LinkedHashMap<String, String> inputPaths;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "connector_run_id")
    private ConnectorRunEntity connectorRun;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "connector_transformation_id", nullable = true)
    private ConnectorTransformerEntity transformation;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ConnectorRunMessagesEntity> messages;
}
