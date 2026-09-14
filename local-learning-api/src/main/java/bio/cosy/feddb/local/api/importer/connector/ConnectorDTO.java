package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectorDTO extends BaseDTO {

    private String name;
    private String description;

    private ConnectorInputConfigDTO inputConfig;
    /**
     * Accepts both a single {@link UploadInfoDTO} and a sheet-keyed map. The annotation makes
     * Quarkus skip the reflection-free serializer for this class (it only supports a fixed set of
     * Jackson annotations); the reflective fallback is intentional.
     */
    @JsonDeserialize(using = SafeUploadInfoDeserializer.class)
    private Map<String, UploadInfoDTO> uploadInfo;
    private Long cohortId;
    private List<ConnectorMappingDTO> schemaMapping;
    private List<ConnectorTransformerDTO> transformer;
    private SheetMergeResultDTO mergeConfig;
    private PivotConfigDTO pivotConfig;

    private TriggerSettingsDTO triggerSettings;
    private ScheduleSettingsDTO scheduleSettings;

    //based on runs
    private ConnectorRunDTO lastRun;
}
