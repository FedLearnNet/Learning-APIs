package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class HarmonizationDTO extends BaseDTO {
    private String name;

    private Long referenceFileId;
    private String referenceKey;
    private List<HarmonizationTargetDTO> targets = new ArrayList<>();

    private Long sourceFileId;
    private String sourceKey;
    private SheetMergeResultDTO sourceMergeConfig;
    private List<ConnectorTransformerDTO> transformer = new ArrayList<>();
    private List<ConnectorMappingDTO> mapping = new ArrayList<>();

    private Long outputFileId;
}
