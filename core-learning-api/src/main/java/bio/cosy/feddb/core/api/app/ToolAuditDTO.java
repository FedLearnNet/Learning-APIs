package bio.cosy.feddb.core.api.app;

import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ToolAuditDTO extends BaseAuthDTO {

    private Long appVersionId;
    private AuditDecision decision;
    private String reason;
}
