package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import jakarta.persistence.*;
import jakarta.ws.rs.DefaultValue;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.LazyGroup;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Setter
@Getter
@Entity
@Table(name = "connector_files",
        indexes = {
                @Index(name = "idx_connector_files_connector_id", columnList = "connector_id"),
                @Index(name = "idx_connector_files_cohort_id", columnList = "cohort_id")
        })
public class ConnectorFilesEntity extends BaseFileEntity {

    @Column(name = "is_support_file")
    @DefaultValue("false")
    private Boolean isSupportFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id")
    private ConnectorEntity connector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private CohortEntity cohort;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upload_info", columnDefinition = "jsonb")
    private List<ConnectorFileUploadInfoDTO> uploadInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upload_settings", columnDefinition = "jsonb")
    private FileParsingSettingsDTO uploadSettings;

    /**
     * Representative raw source sample used for interactive previews. It is
     * intentionally limited to ten physical rows per logical table and is not
     * a complete patient history or a guarantee that every pivot value exists.
     */
    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("previewData")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_data", columnDefinition = "jsonb")
    private Map<String, TableSample> previewData;

    @Column(name = "transformed_statistics_cache_key", length = 64)
    private String transformedStatisticsCacheKey;

    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("transformedStatistics")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transformed_statistics", columnDefinition = "jsonb")
    private List<ColumnProfile> transformedStatistics;
}
