package bio.cosy.feddb.local.api.importer.run.preview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewStageDTO {

    private int stepIndex;

    private Long transformerId;
    private String transformerName;

    private boolean cached;

    private String fingerprint;

    private Date cachedAt;

    private Integer rowCount;
    private boolean appBased;
    private boolean requiresRun;

    private Integer blockedByStep;
}
