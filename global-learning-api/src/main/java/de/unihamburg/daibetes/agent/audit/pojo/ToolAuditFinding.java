package de.unihamburg.daibetes.agent.audit.pojo;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class ToolAuditFinding {

    @Description("Category of the risk")
    private AuditFindingCategory category;

    @Description("Severity for patient privacy and system safety")
    private AuditSeverity severity;

    @Description("Short title, max 80 characters")
    private String title;

    @Description("What the code does and why it is a risk, 1-3 sentences in plain language")
    private String description;

    @Description("Relative path of the affected file, null if not file specific")
    private String filePath;

    @Description("First affected line number, null if unknown")
    private Integer line;

    @Description("Short code excerpt or scan entry proving the finding, max 300 characters, never real patient data")
    private String evidence;

    @Description("Source of the finding: SOURCE_REVIEW, SAST, VULNERABILITY_SCAN or MALWARE_SCAN")
    private String source;

    @Description("Concrete fix for the tool author")
    private String recommendation;
}
