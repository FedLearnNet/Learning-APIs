package de.unihamburg.daibetes.agent.audit.pojo;

import de.unihamburg.daibetes.agent.audit.ToolLlmAuditDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolLlmAuditRequest {
    private ToolLlmAuditDTO audit;
    private boolean created;
}
