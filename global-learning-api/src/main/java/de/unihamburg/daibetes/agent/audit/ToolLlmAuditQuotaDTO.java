package de.unihamburg.daibetes.agent.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolLlmAuditQuotaDTO {
    private boolean enabled;
    private boolean unlimited;
    private int limit;
    private int used;
    private int remaining;
    // When the oldest request inside the window expires, null without usage
    private Date nextSlotAt;
}
