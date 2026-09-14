package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "app_connectors",
        indexes = {
                @Index(name = "idx_app_connectors_cohort_id", columnList = "cohort_id")
        })
@Getter
@Setter
public class ConnectorEntity extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private CohortEntity cohort;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_mapping", columnDefinition = "jsonb")
    private List<ConnectorMappingDTO> schemaMapping;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_config", columnDefinition = "jsonb")
    private ConnectorInputConfigDTO inputConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upload_info", columnDefinition = "jsonb")
    private Map<String, UploadInfoDTO> uploadInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "merge_config", columnDefinition = "jsonb")
    private SheetMergeResultDTO mergeConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pivot_config", columnDefinition = "jsonb")
    private PivotConfigDTO pivotConfig;

    @OneToMany(
            mappedBy = "connector",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<ConnectorTransformerEntity> transformers = new ArrayList<>();

    @OneToMany(
            mappedBy = "connector",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<ConnectorFilesEntity> files = new ArrayList<>();

    @OneToMany(
            mappedBy = "connector",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("id DESC")
    private List<ConnectorRunEntity> runs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type")
    private ConnectorTriggerTypeEnum triggerType;

    @Column(name = "trigger_source_connector_id")
    private Long triggerSourceConnectorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule_settings", columnDefinition = "jsonb")
    private ScheduleSettingsDTO scheduleSettings;

}
