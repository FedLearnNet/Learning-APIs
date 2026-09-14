package bio.cosy.feddb.local.api.importer.validation;

import bio.cosy.feddb.local.api.importer.connector.SafeUploadInfoDeserializer;
import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.PivotConfigDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.extract.UploadInfoDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PreviewValidationRequestDTO {
    private List<PreviewValidationRequestElementDTO> elements;

    @NotNull(message = "Cohort ID is required")
    private Long cohortId;

    @NotNull(message = "Input configuration is required")
    private ConnectorInputConfigDTO inputConfig;
    /** Same two accepted shapes as the {@code uploadInfo} field on {@code ConnectorDTO}. */
    @JsonDeserialize(using = SafeUploadInfoDeserializer.class)
    private Map<String, UploadInfoDTO> uploadInfo;

    private List<ConnectorMappingDTO> schemaMapping;
    private SheetMergeResultDTO mergeConfig;
    private PivotConfigDTO pivotConfig;

    /** Transformer pipeline, so categories are computed on the transformed data. */
    private List<ConnectorTransformerDTO> transformer;
}
