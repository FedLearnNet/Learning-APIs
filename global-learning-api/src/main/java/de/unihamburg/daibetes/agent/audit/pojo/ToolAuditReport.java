package de.unihamburg.daibetes.agent.audit.pojo;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolAuditReport {

    @Description("Overall verdict: SAFE, SAFE_WITH_CONCERNS, UNSAFE or INCONCLUSIVE")
    private AuditVerdict verdict;

    @Description("Highest severity among the findings, INFO if there are none")
    private AuditSeverity riskLevel;

    @Description("Summary for clinicians without security background, 2-4 sentences, no jargon")
    private String clinicianSummary;

    @Description("Technical summary for auditors and tool authors, max 6 sentences")
    private String technicalSummary;

    @Description("What happens to patient data: what is read, what leaves the site through logs, metrics, outputs, model weights or network")
    private String dataFlowSummary;

    @Description("Concrete risks, most severe first")
    private List<ToolAuditFinding> findings = new ArrayList<>();

    @Description("Good practices observed, short bullet sentences")
    private List<String> positives = new ArrayList<>();

    @Description("What could not be checked, e.g. truncated files, missing scans or missing source")
    private List<String> limitations = new ArrayList<>();
}
