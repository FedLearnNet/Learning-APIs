package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.ToolAuditDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolAuditCombinationDTO {
    private FederatedAppDetailDTO appDetail;
    private ToolAuditDTO audit;
}
