package bio.cosy.feddb.local.api.importer.run.preview.cache;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;


@Setter
@Getter
@Entity
@Table(name = "connector_preview_transformation_cache",
        indexes = {
                @Index(name = "idx_preview_cache_connector_id", columnList = "connector_id"),
                @Index(name = "idx_preview_cache_connector_step", columnList = "connector_id, step_index")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uc_preview_cache_connector_step",
                columnNames = {"connector_id", "step_index"}))
public class ConnectorPreviewTransformationCacheEntity extends BaseEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "connector_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ConnectorEntity connector;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "connector_transformer_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ConnectorTransformerEntity transformer;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rows_data", columnDefinition = "jsonb")
    private List<Map<String, Object>> rows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns_data", columnDefinition = "jsonb")
    private List<String> columns;

    @Column(name = "row_count")
    private Integer rowCount;
}
