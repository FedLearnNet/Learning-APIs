package de.unihamburg.daibetes.agent.audit;

import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.agent.audit.pojo.AuditSeverity;
import de.unihamburg.daibetes.agent.audit.pojo.AuditVerdict;
import de.unihamburg.daibetes.agent.audit.pojo.ToolAuditReport;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class ToolLlmAuditDTO extends BaseDTO {
    private Long appVersionId;
    private ToolLlmAuditStatus status;
    private String commitHash;
    private AuditVerdict verdict;
    private AuditSeverity riskLevel;
    private ToolAuditReport report;
    private String errorMessage;
    private String modelName;
    private boolean sourceAvailable;
    private boolean sourceTruncated;
    private int reviewedFiles;
    private int totalFiles;
    private Date startedAt;
    private Date finishedAt;
}
