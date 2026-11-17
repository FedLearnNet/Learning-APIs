package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "harmonizations",
        indexes = {
                @Index(name = "idx_harmonizations_keycloak_id", columnList = "keycloak_id")
        })
@Getter
@Setter
public class HarmonizationEntity extends BaseAuthEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "reference_file_id")
    private Long referenceFileId;

    @Column(name = "reference_key")
    private String referenceKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "targets", columnDefinition = "jsonb")
    private List<HarmonizationTargetDTO> targets;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @Column(name = "source_key")
    private String sourceKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_merge_config", columnDefinition = "jsonb")
    private SheetMergeResultDTO sourceMergeConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transformer", columnDefinition = "jsonb")
    private List<ConnectorTransformerDTO> transformer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping", columnDefinition = "jsonb")
    private List<ConnectorMappingDTO> mapping;

    @Column(name = "output_file_id")
    private Long outputFileId;
}
