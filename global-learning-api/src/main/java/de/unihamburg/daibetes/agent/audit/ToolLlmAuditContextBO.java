package de.unihamburg.daibetes.agent.audit;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.MalwareFindingDTO;
import bio.cosy.feddb.core.api.pipeline.MalwareScanResultDTO;
import bio.cosy.feddb.core.api.pipeline.SastFindingDTO;
import bio.cosy.feddb.core.api.pipeline.SastScanResultDTO;
import bio.cosy.feddb.core.api.pipeline.SecurityScanResultDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditInput;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditSourceSection;
import de.unihamburg.daibetes.agent.audit.source.ToolSourceFile;
import de.unihamburg.daibetes.agent.audit.source.ToolSourceSnapshot;
import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ToolLlmAuditContextBO {

    // Bumped on prompt changes so already audited versions can be audited again
    static final String PROMPT_VERSION = "1";

    private static final int MAX_LISTED_PATHS = 300;

    private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "ERROR", "HIGH", "WARNING", "MEDIUM",
            "LOW", "INFO", "UNKNOWN");

    private static final Set<String> DEPENDENCY_FILES = Set.of("requirements.txt", "pyproject.toml", "setup.py",
            "setup.cfg", "environment.yml", "environment.yaml", "pipfile", "conda.yml", "description");

    private static final Set<String> CODE_EXTENSIONS = Set.of("py", "sh", "bash", "r", "js", "ts", "java", "jl",
            "ipynb", "pl", "rb", "go", "c", "cpp", "h");

    private static final Set<String> LOW_VALUE_FILES = Set.of("poetry.lock", "package-lock.json", "yarn.lock",
            "pipfile.lock", "uv.lock", "license", "license.md", "license.txt");

    @Inject
    FLNetConfig config;

    @Inject
    ObjectMapper objectMapper;

    private ObjectMapper sortedMapper;

    @PostConstruct
    void init() {
        sortedMapper = objectMapper.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    }

    // Scan times are left out, a weekly rescan with equal results keeps the fingerprint
    public String fingerprintOf(ToolLlmAuditInput input, String modelName) {
        AppPublishInfoDTO info = input.getPublishInfo();
        StringBuilder sb = new StringBuilder(PROMPT_VERSION).append('|').append(modelName)
                .append('|').append(StringUtils.defaultString(input.getCommitHash()));
        if (info != null) {
            if (info.getSastScanResult() != null) {
                SastScanResultDTO sast = info.getSastScanResult();
                sb.append("|sast:").append(sast.isSuccess()).append(sast.isComplete());
                sast.getFindings().forEach(f -> sb.append(';').append(f.getRuleId()).append('@')
                        .append(f.getFilePath()).append(':').append(f.getStartLine()));
            }
            if (info.getVulnerabilityScanResult() != null) {
                sb.append("|vuln:").append(json(info.getVulnerabilityScanResult().getRawReport()));
            }
            if (info.getMalwareScanResult() != null) {
                MalwareScanResultDTO malware = info.getMalwareScanResult();
                sb.append("|malware:").append(malware.isSuccess()).append(malware.getInfectedCount());
                malware.getFindings().forEach(f -> sb.append(';').append(f.getSignature()).append('@')
                        .append(f.getFilePath()));
            }
        }
        return DigestUtils.sha256Hex(sb.toString());
    }

    public String metadataOf(ToolLlmAuditInput input) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Name", input.getAppName());
        line(sb, "Type", input.getAppType());
        line(sb, "Version", input.getVersion());
        line(sb, "Description", input.getShortDescription());
        line(sb, "Source repository", input.getSourceUrl());
        line(sb, "Audited commit", StringUtils.defaultIfBlank(input.getCommitHash(), "unknown, latest main"));
        line(sb, "Declares internet access", String.valueOf(input.isNeedsInternetAccess()));
        line(sb, "Declares host access", String.valueOf(input.isNeedsHostAccess()));
        line(sb, "Supports federated learning", String.valueOf(input.isSupportsFederatedLearning()));
        list(sb, "Declared inputs", input.getInputs());
        list(sb, "Declared outputs", input.getOutputs());
        list(sb, "Hyperparameters", input.getHyperParams());
        return sb.toString();
    }

    public String sastOf(AppPublishInfoDTO info) {
        SastScanResultDTO sast = info == null ? null : info.getSastScanResult();
        if (sast == null) {
            return "Not available. The static analysis did not run for this version.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(sast.getTool()).append(' ').append(StringUtils.defaultString(sast.getVersion()))
                .append(", success: ").append(sast.isSuccess()).append(", complete: ").append(sast.isComplete())
                .append(", scanned files: ").append(sast.getScannedFiles()).append('\n');
        if (sast.getSummary() != null) {
            sb.append("Summary: ").append(sast.getSummary().getError()).append(" error, ")
                    .append(sast.getSummary().getWarning()).append(" warning, ")
                    .append(sast.getSummary().getInfo()).append(" info\n");
        }
        List<SastFindingDTO> findings = sast.getFindings().stream()
                .sorted(Comparator.comparingInt(f -> severityRank(f.getSeverity())))
                .toList();
        if (findings.isEmpty()) {
            sb.append("No findings.\n");
        }
        int limit = config.llmAudit().maxSastFindings();
        findings.stream().limit(limit).forEach(f -> sb.append("- [").append(f.getSeverity()).append("] ")
                .append(f.getRuleId()).append(" at ").append(relativePath(f.getFilePath())).append(':')
                .append(f.getStartLine()).append(": ").append(oneLine(f.getMessage(), 300)).append('\n'));
        if (findings.size() > limit) {
            sb.append("... ").append(findings.size() - limit).append(" further findings omitted\n");
        }
        if (!sast.getErrors().isEmpty()) {
            sb.append("Scan errors: ").append(sast.getErrors().size()).append('\n');
        }
        return sb.toString();
    }

    public String vulnerabilitiesOf(AppPublishInfoDTO info) {
        SecurityScanResultDTO scan = info == null ? null : info.getVulnerabilityScanResult();
        if (scan == null) {
            return "Not available. The image was not scanned.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(scan.getTool()).append(", image: ").append(scan.getTarget())
                .append(", success: ").append(scan.isSuccess()).append('\n');
        if (scan.getSummary() != null) {
            sb.append("Summary: ").append(scan.getSummary().getCritical()).append(" critical, ")
                    .append(scan.getSummary().getHigh()).append(" high, ")
                    .append(scan.getSummary().getMedium()).append(" medium, ")
                    .append(scan.getSummary().getLow()).append(" low\n");
        }
        List<Map<String, Object>> vulnerabilities = vulnerabilityEntries(scan.getRawReport());
        int limit = config.llmAudit().maxVulnerabilities();
        vulnerabilities.stream().limit(limit).forEach(v -> sb.append("- [").append(v.get("Severity")).append("] ")
                .append(v.get("VulnerabilityID")).append(" in ").append(v.get("PkgName")).append(' ')
                .append(v.get("InstalledVersion")).append(" (target ").append(v.get("_target")).append(", fixed in ")
                .append(StringUtils.defaultIfBlank(String.valueOf(v.getOrDefault("FixedVersion", "")), "no fix"))
                .append("): ").append(oneLine(String.valueOf(v.getOrDefault("Title", "")), 200)).append('\n'));
        if (vulnerabilities.size() > limit) {
            sb.append("... ").append(vulnerabilities.size() - limit).append(" further vulnerabilities omitted\n");
        }
        return sb.toString();
    }

    public String malwareOf(AppPublishInfoDTO info) {
        MalwareScanResultDTO malware = info == null ? null : info.getMalwareScanResult();
        if (malware == null) {
            return "Not available. The malware scan did not run for this version.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(malware.getTool()).append(", success: ").append(malware.isSuccess())
                .append(", infected files: ").append(malware.getInfectedCount()).append('\n');
        for (MalwareFindingDTO finding : malware.getFindings()) {
            sb.append("- ").append(relativePath(finding.getFilePath())).append(": ")
                    .append(oneLine(finding.getSignature(), 200)).append('\n');
        }
        return sb.toString();
    }

    public ToolLlmAuditSourceSection sourceOf(ToolSourceSnapshot snapshot, AppPublishInfoDTO info) {
        if (snapshot == null || snapshot.getFiles().isEmpty()) {
            return new ToolLlmAuditSourceSection("Source code not available. The repository could not be downloaded.",
                    false, false, 0, 0);
        }
        Set<String> flagged = flaggedPaths(info);
        List<ToolSourceFile> ordered = snapshot.getFiles().stream()
                .sorted(Comparator.comparingInt((ToolSourceFile f) -> priority(f.getPath(), flagged))
                        .thenComparing(ToolSourceFile::getPath))
                .toList();

        StringBuilder sb = new StringBuilder("Repository files:\n");
        snapshot.getFiles().stream().limit(MAX_LISTED_PATHS).forEach(f -> sb.append("  ").append(f.getPath()).append('\n'));
        if (snapshot.getFiles().size() > MAX_LISTED_PATHS) {
            sb.append("  ... ").append(snapshot.getFiles().size() - MAX_LISTED_PATHS).append(" more\n");
        }
        if (!snapshot.getSkippedPaths().isEmpty()) {
            sb.append("Binary, data or oversized files not shown: ")
                    .append(String.join(", ", snapshot.getSkippedPaths().stream().limit(50).toList())).append('\n');
        }
        sb.append('\n');

        int budget = config.llmAudit().sourceCharBudget();
        int reviewed = 0;
        boolean truncated = false;
        List<String> left = new ArrayList<>();
        for (ToolSourceFile file : ordered) {
            if (priority(file.getPath(), flagged) >= 9) {
                continue;
            }
            String rendered = renderFile(file);
            if (sb.length() + rendered.length() > budget) {
                truncated = true;
                left.add(file.getPath());
                continue;
            }
            if (rendered.contains("[truncated")) {
                truncated = true;
            }
            sb.append(rendered);
            reviewed++;
        }
        if (!left.isEmpty()) {
            sb.append("Not included due to size limits: ")
                    .append(String.join(", ", left.stream().limit(100).toList())).append('\n');
        }
        return new ToolLlmAuditSourceSection(sb.toString(), true, truncated, reviewed, snapshot.getFiles().size());
    }

    private String renderFile(ToolSourceFile file) {
        String content = file.getPath().toLowerCase(Locale.ROOT).endsWith(".ipynb")
                ? notebookCode(file.getContent()) : file.getContent();
        String[] lines = content.split("\\R", -1);
        int maxChars = config.llmAudit().maxFileChars();
        StringBuilder sb = new StringBuilder();
        sb.append("=== FILE: ").append(file.getPath()).append(" (").append(lines.length).append(" lines) ===\n");
        int start = sb.length();
        for (int i = 0; i < lines.length; i++) {
            if (sb.length() - start > maxChars) {
                sb.append("... [truncated, ").append(lines.length - i).append(" more lines]\n");
                break;
            }
            sb.append(i + 1).append("| ").append(lines[i]).append('\n');
        }
        return sb.append('\n').toString();
    }

    // Notebook outputs may contain real data, only cell sources are kept
    private String notebookCode(String json) {
        try {
            JsonNode cells = objectMapper.readTree(json).path("cells");
            StringBuilder sb = new StringBuilder();
            int index = 0;
            for (JsonNode cell : cells) {
                sb.append("# --- cell ").append(++index).append(" (").append(cell.path("cell_type").asString())
                        .append(") ---\n");
                JsonNode source = cell.path("source");
                if (source.isArray()) {
                    source.forEach(part -> sb.append(part.asString()));
                } else {
                    sb.append(source.asString());
                }
                sb.append('\n');
            }
            return sb.toString();
        } catch (JacksonException e) {
            return "[notebook could not be parsed]";
        }
    }

    private int priority(String path, Set<String> flagged) {
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (flagged.stream().anyMatch(lower::endsWith)) {
            return 0;
        }
        if (LOW_VALUE_FILES.contains(name)) {
            return 9;
        }
        if (name.startsWith("dockerfile") || name.endsWith(".dockerfile") || name.equals("app.yml")
                || name.equals("app.yaml") || name.startsWith("docker-compose")) {
            return 1;
        }
        if (DEPENDENCY_FILES.contains(name) || name.startsWith("requirements")) {
            return 2;
        }
        boolean test = lower.startsWith("test/") || lower.startsWith("tests/") || lower.contains("/tests/")
                || name.startsWith("test_") || name.endsWith("_test.py");
        if (CODE_EXTENSIONS.contains(extension)) {
            return test ? 6 : extension.equals("py") ? 3 : 4;
        }
        return test ? 7 : 5;
    }

    private Set<String> flaggedPaths(AppPublishInfoDTO info) {
        List<String> paths = new ArrayList<>();
        if (info != null && info.getSastScanResult() != null) {
            info.getSastScanResult().getFindings().forEach(f -> paths.add(f.getFilePath()));
        }
        if (info != null && info.getMalwareScanResult() != null) {
            info.getMalwareScanResult().getFindings().forEach(f -> paths.add(f.getFilePath()));
        }
        return Set.copyOf(paths.stream().filter(StringUtils::isNotBlank)
                .map(p -> relativePath(p).toLowerCase(Locale.ROOT)).toList());
    }

    // Scanners report absolute paths inside the build workspace
    private String relativePath(String path) {
        if (path == null) {
            return "unknown";
        }
        String normalized = path.replace('\\', '/');
        for (String marker : List.of("/repo/", "/workspace/", "/src/app/", "/app/")) {
            int index = normalized.indexOf(marker);
            if (index >= 0) {
                return normalized.substring(index + marker.length());
            }
        }
        return StringUtils.removeStart(normalized, "/");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> vulnerabilityEntries(Map<String, Object> rawReport) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (rawReport == null) {
            return entries;
        }
        rawReport.forEach((target, value) -> {
            if (value instanceof Collection<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> entry = new LinkedHashMap<>((Map<String, Object>) map);
                        entry.put("_target", target);
                        entries.add(entry);
                    }
                }
            }
        });
        entries.sort(Comparator.comparingInt(e -> severityRank(String.valueOf(e.get("Severity")))));
        return entries;
    }

    private int severityRank(String severity) {
        int index = SEVERITY_ORDER.indexOf(StringUtils.defaultString(severity).toUpperCase(Locale.ROOT));
        return index < 0 ? SEVERITY_ORDER.size() : index;
    }

    private String json(Object value) {
        try {
            return sortedMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }

    private void line(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(StringUtils.defaultIfBlank(value, "-")).append('\n');
    }

    private void list(StringBuilder sb, String label, List<String> values) {
        sb.append(label).append(": ");
        if (values == null || values.isEmpty()) {
            sb.append("-\n");
            return;
        }
        sb.append('\n');
        values.forEach(v -> sb.append("  - ").append(v).append('\n'));
    }

    private String oneLine(String text, int max) {
        return StringUtils.abbreviate(StringUtils.normalizeSpace(StringUtils.defaultString(text)), max);
    }
}
