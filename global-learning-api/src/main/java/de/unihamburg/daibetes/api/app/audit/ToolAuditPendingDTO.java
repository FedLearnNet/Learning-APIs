package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolAuditPendingDTO {
    private FederatedAppDetailDTO appDetail;
    private Integer openCount;

    public ToolAuditPendingDTO(FederatedAppDetailDTO appDetail) {
        this.appDetail = appDetail;
    }
}
