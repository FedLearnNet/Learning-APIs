package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "connector_transformers",
        indexes = {
                @Index(name = "idx_connector_transformers_connector_id", columnList = "connector_id")
        })
@Getter
@Setter
public class ConnectorTransformerEntity extends BaseEntity {

    @Column(name = "module", nullable = false, length = 255)
    private String moduleName;

    @Column(name = "method_name", nullable = false, length = 255)
    private String methodName;

    @Column(name = "position", nullable = false)
    private Integer position;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_mapping", columnDefinition = "jsonb")
    private Map<String, Object> inputMapping;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "return_mapping", columnDefinition = "jsonb")
    private Map<String, Object> returnMapping;

    @Column(name = "column_name", length = 255)
    private String column;

    @Column(name = "app_version_id")
    private Integer appVersionId;

    @Column(name = "app_image", length = 1000)
    private String appImage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hyperparams", columnDefinition = "jsonb")
    private Map<String, Object> hyperparams;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id")
    private ConnectorEntity connector;

    /**
     * Compares all business/content fields, ignoring identity (id, version, timestamps),
     * position (handled separately during sync), and the connector relationship.
     */
    public boolean contentEquals(ConnectorTransformerEntity other) {
        if (other == null) return false;
        return Objects.equals(moduleName, other.moduleName)
                && Objects.equals(methodName, other.methodName)
                && Objects.equals(column, other.column)
                && Objects.equals(appVersionId, other.appVersionId)
                && Objects.equals(appImage, other.appImage)
                && mapsEqual(inputMapping, other.inputMapping)
                && mapsEqual(returnMapping, other.returnMapping)
                && mapsEqual(hyperparams, other.hyperparams);
    }

    private static boolean mapsEqual(Map<String, ?> a, Map<String, ?> b) {
        boolean aEmpty = a == null || a.isEmpty();
        boolean bEmpty = b == null || b.isEmpty();
        if (aEmpty && bEmpty) return true;
        if (aEmpty || bEmpty) return false;
        return a.equals(b);
    }
}
