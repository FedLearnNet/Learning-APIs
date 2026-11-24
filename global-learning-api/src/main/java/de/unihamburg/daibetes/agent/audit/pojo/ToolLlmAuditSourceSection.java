package de.unihamburg.daibetes.agent.audit.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolLlmAuditSourceSection {
    private String text;
    private boolean available;
    private boolean truncated;
    private int reviewedFiles;
    private int totalFiles;
}
