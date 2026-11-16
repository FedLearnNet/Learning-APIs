package de.unihamburg.daibetes.agent.audit.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolLlmAuditCaller {
    private String keycloakId;
    private boolean admin;
    private boolean auditor;
}
