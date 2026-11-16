package de.unihamburg.daibetes.agent.audit;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.agent.audit.pojo.AuditSeverity;
import de.unihamburg.daibetes.agent.audit.pojo.AuditVerdict;
import de.unihamburg.daibetes.agent.audit.pojo.ToolAuditReport;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;

@Setter
@Getter
@Entity
@Table(name = "tool_llm_audit")
public class ToolLlmAuditEntity extends BaseAuthEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_version_id", nullable = false)
    private FederatedAppVersionEntity appVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ToolLlmAuditStatus status = ToolLlmAuditStatus.PENDING;

    @Column(name = "commit_hash", length = 100)
    private String commitHash;

    // Hash over commit and scan results, an unchanged one needs no new audit
    @Column(length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AuditVerdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private AuditSeverity riskLevel;

    @Column(columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private ToolAuditReport report;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "source_available")
    private boolean sourceAvailable;

    @Column(name = "source_truncated")
    private boolean sourceTruncated;

    @Column(name = "reviewed_files")
    private int reviewedFiles;

    @Column(name = "total_files")
    private int totalFiles;

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "finished_at")
    private Date finishedAt;
}
