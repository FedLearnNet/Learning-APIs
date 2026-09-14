package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.AuditDecision;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "tool_audit")
public class ToolAuditEntity extends BaseAuthEntity {

    @Enumerated(EnumType.STRING)
    private AuditDecision decision;

    @Column(columnDefinition = "TEXT")
    private String reason;


    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "app_version_id", nullable = false)
    private FederatedAppVersionEntity appVersion;

}
