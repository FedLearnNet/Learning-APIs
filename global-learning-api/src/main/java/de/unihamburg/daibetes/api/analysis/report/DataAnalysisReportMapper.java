package de.unihamburg.daibetes.api.analysis.report;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageType;
import bio.cosy.feddb.core.api.run.RunTimingMetric;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mapstruct.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(config = QuarkusMappingConfig.class)
@ApplicationScoped
public abstract class DataAnalysisReportMapper {

    @Inject
    HyperParamMapper hyperParamMapper;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    ModelBO modelBO;

    @Inject
    FileBO fileBO;

    // Single source of truth for the overhead flag; runtime aggregates are always computed.
    @Inject
    @ConfigProperty(name = "posymed.runtime.overhead.enabled", defaultValue = "false")
    boolean overheadEnabled;

    @Mappings({
            @Mapping(target = "allFiles", source = "files", qualifiedByName = "filesToCards"),
            @Mapping(target = "meta", expression = "java(mapMeta(run))"),
            @Mapping(target = "overview", expression = "java(mapOverview(run))"),
            @Mapping(target = "performance", expression = "java(mapPerformance(run))"),
            @Mapping(target = "runs", source = "run", qualifiedByName = "mapRuns"),
    })
    public abstract ReportView runToReport(DataAnalysisDetailDTO run);

    @Mappings({

            @Mapping(target = "generatedAt", expression = "java(java.time.OffsetDateTime.now().toString())"),
            @Mapping(target = "subtitle", source = "keycloakId"),
            @Mapping(target = "title", source = "name")
    })
    public abstract ReportView.Meta mapMeta(DataAnalysisDetailDTO run);

    @Mappings({
            @Mapping(target = "totalFiles", expression = "java(run.getFiles().size())"),
            @Mapping(target = "totalMessages", source = "run", qualifiedByName = "mapTotalMessages"),
            @Mapping(target = "totalRuns", source = "run", qualifiedByName = "mapTotalRuns"),
    })
    public abstract ReportView.Overview mapOverview(DataAnalysisDetailDTO run);


    @Mappings({
            @Mapping(target = "downloadUrl", source = "file.downloadUrl"),
            @Mapping(target = "logicalName", source = "name"),
            @Mapping(target = "sizeBytes", source = "file.size"),
            @Mapping(target = "contentType", source = "file.contentType"),
            @Mapping(target = "fileName", source = "file.fileName"),
            @Mapping(target = "fileContent", source = "file", qualifiedByName = "mapFileContent"),
            @Mapping(target = "isImage", source = "file", qualifiedByName = "mapIsImage"),
    })
    public abstract ReportView.FileCard fileToCard(DataAnalysisFileDTO file);

    @Mappings({
            @Mapping(target = "inputsJson", source = "run.inputs", qualifiedByName = "mapToJson"),
            @Mapping(target = "hyperParamsJson", source = "run.hyperParams", qualifiedByName = "mapToJson"),
            @Mapping(target = "outputs", source = "run.outputFiles", qualifiedByName = "filesToCards"),
            @Mapping(target = "inputs", source = "run.inputFiles", qualifiedByName = "filesToCards"),
            @Mapping(target = "resultJson", source = "run.result", qualifiedByName = "mapToJson"),
            @Mapping(target = "toolDescription", ignore = true), //after mapping
            @Mapping(target = "toolName", ignore = true), //after mapping
            @Mapping(target = "type", ignore = true),
            @Mapping(target = "hyperParamsConfig", ignore = true), //after mapping
            @Mapping(target = "inputConfig", ignore = true), //after mapping
            @Mapping(target = "resultConfig", ignore = true),
            @Mapping(target = "hyperParamsEven", expression = "java(run.getHyperParams().size() % 2 == 0)"),
            @Mapping(target = "inputConfigEven", expression = "java(run.getInputs().size() % 2 == 0)"),
            @Mapping(target = "resultConfigEven", expression = "java(run.getResult().size() % 2 == 0)"),
    })
    public abstract ReportView.RunView runToView(DataAnalysisResultDTO run);


    @AfterMapping
    protected void afterDetailMapping(DataAnalysisResultDTO run, @MappingTarget ReportView.RunView view) {
        Long appVersionId = run.getAppVersionId();
        FederatedAppDetailDTO tool = null;
        if (appVersionId == null && run.getModelVersionId() != null) {
            Long versionId = modelBO.getById(run.getModelVersionId()).getFederatedAppVersionId();
            tool = federatedAppBO.getByVersionId(versionId);
        } else {
            tool = federatedAppBO.getByVersionId(appVersionId);
        }

        if (run.getWorkflowRunId() != null) {
            view.setType(String.format("Workflow (%s/%s)", run.getExecutionOrder() + 1, run.getMaxWorkflowSteps()));
        } else {
            view.setType("App Prediction");
        }

        view.setToolName(String.format("%s v%s", tool.getName(), tool.getVersion()));
        view.setToolDescription(tool.getShortDescription());

        view.setHyperParamsConfig(tool.getAppConfig().getHyperparams().stream().map(h ->
                mapConfigEntry(run.getHyperParams(), h.getName(), h.getVariableName(), h.getDescription(), null)
        ).toList());

        view.setInputConfig(tool.getAppConfig().getInput().stream().map(h ->
                mapConfigEntry(run.getInputs(), h.getName(), h.getVariableName(), h.getDescription(), run.getInputFiles())
        ).toList());

        view.setResultConfig(tool.getAppConfig().getOutput().stream().map(h ->
                mapConfigEntry(run.getResult(), h.getName(), h.getVariableName(), h.getDescription(), run.getOutputFiles())
        ).toList());

        // Performance: runtime is always shown; the overhead breakdown only when the flag is on.
        Long runtimeMs = timing(run, RunTimingMetric.RUNTIME);
        Long overheadTotalMs = timing(run, RunTimingMetric.OVERHEAD_TOTAL);
        view.setRuntimeMs(runtimeMs);
        view.setRuntimeLabel(formatDuration(runtimeMs));
        boolean hasOverhead = overheadEnabled && overheadTotalMs != null;
        view.setHasOverhead(hasOverhead);
        if (hasOverhead) {
            view.setStartupLabel(formatDuration(timing(run, RunTimingMetric.OVERHEAD_STARTUP)));
            view.setTeardownLabel(formatDuration(timing(run, RunTimingMetric.OVERHEAD_TEARDOWN)));
            view.setTotalOverheadLabel(formatDuration(overheadTotalMs));
            Long wallTotal = runtimeMs == null ? null : runtimeMs + overheadTotalMs;
            view.setWallTotalLabel(formatDuration(wallTotal));
            view.setOverheadPercentLabel(formatPercent(overheadTotalMs, runtimeMs));
        }
    }

    /** Null-safe read of a single timing metric (ms) from a run's metadata. */
    private Long timing(DataAnalysisResultDTO run, RunTimingMetric metric) {
        return run.getMeta() == null ? null : run.getMeta().timing(metric);
    }

    /** Aggregate runtime (mean ± SD, sample SD) across the prediction runs in this analysis. */
    public ReportView.Performance mapPerformance(DataAnalysisDetailDTO run) {
        List<Long> runtimes = run.getMessages().stream()
                .filter(m -> ModelWorkflowChatMessageType.isPrediction(m.getType()))
                .map(m -> timing((DataAnalysisResultDTO) m.getMessage(), RunTimingMetric.RUNTIME))
                .filter(Objects::nonNull)
                .toList();
        if (runtimes.isEmpty()) {
            return new ReportView.Performance(false, 0, "—", "—", overheadEnabled);
        }
        double mean = runtimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double sd = 0;
        if (runtimes.size() > 1) {
            double variance = runtimes.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .sum() / (runtimes.size() - 1);
            sd = Math.sqrt(variance);
        }
        return new ReportView.Performance(true, runtimes.size(),
                formatDuration(Math.round(mean)), formatDuration(Math.round(sd)), overheadEnabled);
    }

    /** Human-readable duration; "—" for null/negative. */
    protected String formatDuration(Long ms) {
        if (ms == null || ms < 0) {
            return "—";
        }
        if (ms < 1000) {
            return ms + " ms";
        }
        double seconds = ms / 1000.0;
        if (seconds < 60) {
            return trimNumber(seconds) + " s";
        }
        long minutes = (long) (seconds / 60);
        long remaining = Math.round(seconds - minutes * 60);
        return remaining == 0 ? minutes + " min" : minutes + " min " + remaining + " s";
    }

    /** Overhead as a percentage of wall-clock total (runtime + overhead); "—" when undefined. */
    protected String formatPercent(Long overheadTotalMs, Long runtimeMs) {
        if (overheadTotalMs == null || runtimeMs == null) {
            return "—";
        }
        long wallTotal = overheadTotalMs + runtimeMs;
        if (wallTotal <= 0) {
            return "—";
        }
        double pct = Math.round(overheadTotalMs * 1000.0 / wallTotal) / 10.0;
        return pct + " %";
    }

    private String trimNumber(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.valueOf(rounded);
    }

    protected ReportView.Configuration mapConfigEntry(LinkedHashMap<String, Object> values,
                                                      String name,
                                                      String variableName,
                                                      String description,
                                                      List<DataAnalysisFileDTO> files) {
        String value = null;
        if (values.containsKey(name)) {
            value = values.get(name).toString();
        }
        if (values.containsKey(variableName)) {
            value = values.get(variableName).toString();
        }
        ReportView.FileCard file = Optional.ofNullable(files)
                .map(fs -> fs.stream()
                        .filter(f -> f.getName().equals(name) || f.getName().equals(variableName))
                        .map(this::fileToCard)
                        .findFirst().orElseGet(() -> null))
                .orElseGet(() -> null);
        return new ReportView.Configuration(name, value, description, file);
    }

    @Named("filesToCards")
    public List<ReportView.FileCard> filesToCards(List<DataAnalysisFileDTO> files) {
        return files.stream().map(this::fileToCard).toList();
    }

    @Named("mapToJson")
    public String mapToJson(LinkedHashMap<String, Object> map) {
        return hyperParamMapper.hyperparamsToJsonString(map);
    }

    @Named("mapTotalMessages")
    public int mapTotalMessages(DataAnalysisDetailDTO run) {
        return Math.toIntExact(run.getMessages()
                .stream()
                .filter(m -> m.getType().equals(ModelWorkflowChatMessageType.CHAT_MESSAGE))
                .count());
    }

    @Named("mapTotalRuns")
    public int mapTotalRuns(DataAnalysisDetailDTO run) {
        return Math.toIntExact(run.getMessages()
                .stream()
                .filter(m -> ModelWorkflowChatMessageType.isPrediction(m.getType()))
                .count());
    }

    @Named("mapRuns")
    public List<ReportView.RunView> mapRuns(DataAnalysisDetailDTO run) {
        return run.getMessages()
                .stream()
                .filter(m -> ModelWorkflowChatMessageType.isPrediction(m.getType()))
                .map(m -> runToView((DataAnalysisResultDTO) m.getMessage()))
                .toList();
    }

    @Named("mapFileContent")
    public String mapFileContent(FileDTO file) {
        FileContentDTO content = fileBO.getFileContentBySecret(file.getId(), file.getSecret());
        if (content.getType().equals(ToolConfigDataType.IMAGE)) {
            return "data:" + file.getContentType() + ";base64," + content.getContent();
        } else if (content.getType().equals(ToolConfigDataType.TEXT)
                || content.getType().equals(ToolConfigDataType.STRING)
                || content.getType().equals(ToolConfigDataType.HTML)) {
            return content.getContent();

        }
        return null;
    }

    @Named("mapIsImage")
    public boolean mapIsImage(FileDTO file) {
        return ToolConfigDataType.fromMimeType(file.getContentType()).equals(ToolConfigDataType.IMAGE);
    }
}
