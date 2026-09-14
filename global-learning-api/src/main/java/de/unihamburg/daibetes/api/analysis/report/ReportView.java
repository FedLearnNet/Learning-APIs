package de.unihamburg.daibetes.api.analysis.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public record ReportView(
        Meta meta,
        Overview overview,
        Performance performance,
        List<RunView> runs,
        List<FileCard> allFiles
) {
    /**
     * Aggregate runtime across the runs in this report (the multi-run reproducibility view).
     * mean/sd are pre-formatted for display; overheadEnabled mirrors the config flag so the
     * template can decide whether to show the overhead columns at all.
     */
    public record Performance(
            boolean hasRuntime,
            int runtimeCount,
            String runtimeMean,
            String runtimeSd,
            boolean overheadEnabled
    ) {
    }

    public record Meta(
            String title,
            String subtitle,
            String generatedAt
    ) {
    }

    public record Overview(
            int totalMessages,
            int totalFiles,
            int totalRuns
    ) {
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    //To set later in aftermapping has to be a class
    public static class RunView {
        String type;                 // PREDICTION / WORKFLOW_PREDICTION
        String status;               // SUCCESS/FAILED/...
        String toolDescription;            // "App vX" or "Workflow"
        String toolName;            // "App vX" or "Workflow"

        String appVersionId;
        String workflowId;
        String modelId;
        String name;

        String hyperParamsJson;
        String inputsJson;
        String resultJson;

        List<FileCard> inputs;
        List<FileCard> outputs;

        List<ReportView.Configuration> hyperParamsConfig;
        List<ReportView.Configuration> inputConfig;
        List<ReportView.Configuration> resultConfig;

        boolean hyperParamsEven;
        boolean inputConfigEven;
        boolean resultConfigEven;

        String lastLog;
        String lastError;

        // Tool lifecycle timing. Raw ms values plus pre-formatted labels for the template.
        // hasOverhead is true only when the overhead breakdown is available and the flag is on.
        Long runtimeMs;
        boolean hasOverhead;
        String runtimeLabel;
        String startupLabel;
        String teardownLabel;
        String totalOverheadLabel;
        String wallTotalLabel;
        String overheadPercentLabel;
    }

    public record Configuration(
            String name,
            String value,
            String description,
            FileCard file
    ) {
    }

    public record FileCard(
            String fileName,
            String contentType,
            long sizeBytes,
            String role,                // input/output
            String logicalName,          // inputName/outputName
            String downloadUrl,
            String fileContent,
            boolean isImage
    ) {
    }
}
