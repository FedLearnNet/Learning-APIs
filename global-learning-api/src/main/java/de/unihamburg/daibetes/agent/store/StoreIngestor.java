package de.unihamburg.daibetes.agent.store;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolOutputConfigDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import de.unihamburg.daibetes.agent.embedding.EmbeddingAO;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigBO;
import de.unihamburg.daibetes.api.store.StoreBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.quarkus.logging.Log;
import io.smallrye.context.api.ManagedExecutorConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@ApplicationScoped
public class StoreIngestor {
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;

    private static final class MetaKeys {
        static final String KIND = "kind"; // "app_summary" | "model_summary"
        static final String APP_ID = "appId";
        static final String APP_VERSION_ID = "appVersionId";
        static final String APP_NAME = "appName";
        static final String APP_TYPE = "appType";
        static final String MODEL_ID = "modelId";
        static final String MODEL_NAME = "modelName";
        static final String MODEL_PUBLISH_STATUS = "modelPublishStatus";
        static final String MODEL_VERSION = "modelVersion";
    }

    @Inject
    PgVectorEmbeddingStore store;
    @Inject
    StoreBO storeBO;
    @Inject
    FederatedAppConfigBO appConfigBO;
    @Inject
    EmbeddingModel embeddingModel;
    @Inject
    EmbeddingAO embeddingAO;

    @Inject
    FLNetConfig config;

    @Inject
    @ManagedExecutorConfig(cleared = ThreadContext.TRANSACTION)
    ManagedExecutor asyncExecutor;

    @ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    Optional<String> apiKey;

    private EmbeddingStoreIngestor ingestor;

    private final AtomicBoolean apiKeyPresent = new AtomicBoolean(false);

    @PostConstruct
    void init() {
        this.ingestor = EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();

        String key = apiKey.map(String::trim).orElse("");
        if (key.isEmpty() || "VIA_ENV".equals(key)) {
            apiKeyPresent.set(false);
            Log.errorf("no embedding API key configured (set QUARKUS_LANGCHAIN4J_OPENAI_API_KEY)");
        } else {
            apiKeyPresent.set(true);
            Log.infof("embedding API key configured");
        }
    }

    public void ingest() {
        if (apiKeyPresent.get()) {
            Log.errorf("Skipping StoreIngestor.ingest(): store ingestion is disabled");
            return;
        }
        Log.infof("Starting StoreIngestor.ingest()");
        for (StoreDTO dto : storeBO.list(null, true)) {
            if (apiKeyPresent.get()) {
                break;
            }
            upsertOne(dto);
        }
    }

    public void ingestAsync(StoreDTO dto) {
        if (apiKeyPresent.get() || dto == null || (dto.getApp() == null && dto.getModel() == null)) {
            return;
        }

        Log.infof("Starting async ingestion for StoreDTO with appId=%s, modelId=%s",
                dto.getApp() != null ? dto.getApp().getId() : "null",
                dto.getModel() != null ? dto.getModel().getId() : "null");

        asyncExecutor.runAsync(() -> upsertOneTransactional(dto))
                .exceptionally(err -> {
                    Log.warnf(err, "Async StoreDTO ingestion failed");
                    return null;
                });
    }

    @ActivateRequestContext
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void upsertOneTransactional(StoreDTO dto) {
        upsertOne(dto);
    }

    public void upsertOne(StoreDTO dto) {
        boolean ignoreStartUpCheck = config.ingestor().ignoreStartUpCheck();
        if (apiKeyPresent.get() || dto == null || (dto.getApp() == null && dto.getModel() == null)) {
            return;
        }

        boolean isModel = dto.getModel() != null;
        if (isModel) {
            Long modelId = dto.getModel().getId();
            boolean hasEmbedding = modelId != null && embeddingAO.hasModelEmbedding(modelId);
            if (hasEmbedding && !ignoreStartUpCheck) {
                Log.infof("Model %s already has embeddings, skipping ingestion.", modelId);
                return;
            }
            if (ignoreStartUpCheck && hasEmbedding) {
                try {
                    embeddingAO.deleteModelEmbeddings(modelId);
                } catch (Exception e) {
                    Log.warnf(e, "Failed to delete old embeddings for model %s", modelId);
                }
            }
            Log.infof("Ingesting model %s", modelId);
        } else {
            Long appId = dto.getApp().getId();
            boolean hasEmbedding = appId != null && embeddingAO.hasAppEmbedding(appId);
            if (hasEmbedding && !ignoreStartUpCheck) {
                Log.infof("App %s already has embeddings, skipping ingestion.", appId);
                return;
            }
            if (ignoreStartUpCheck && hasEmbedding) {
                try {
                    embeddingAO.deleteAppEmbeddings(appId);
                } catch (Exception e) {
                    Log.warnf(e, "Failed to delete old embeddings for app %s", appId);
                }
            }
            Log.infof("Ingesting app %s", appId);
        }

        List<Document> docs = toDocuments(dto);
        for (Document doc : docs) {
            try {
                ingestor.ingest(doc);
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    Log.errorf("embedding API rejected the API key: " + e.getMessage());
                    apiKeyPresent.set(false);
                    return;
                }
                Log.warnf("Error while ingesting document: %s", e.getMessage());
            }
        }
    }


    private static boolean isAuthFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationException) {
                return true;
            }
            if (t instanceof HttpException http && (http.statusCode() == 401 || http.statusCode() == 403)) {
                return true;
            }
        }
        return false;
    }

    public List<Document> toDocuments(StoreDTO storeDTO) {
        List<Document> docs = new ArrayList<>(4);
        FederatedAppDTO app = storeDTO.getApp();
        ModelDTO model = storeDTO.getModel();

        if (app != null) {
            docs.addAll(buildAppDocs(app));
        }
        if (model != null) {
            docs.addAll(buildModelDocs(model, app));
        }
        return docs;
    }

    /* ----------------------------- APP ------------------------------ */

    public List<Document> buildAppDocs(FederatedAppDTO app) {
        ToolConfigsDTO cfg = null;
        if (app.getLatestVersionId() != null) {
            try {
                cfg = appConfigBO.findByAppVersionId(app.getLatestVersionId());
            } catch (Exception e) {
                Log.warnf(e, "Config lookup failed for appVersionId=%s", app.getLatestVersionId());
            }
        }

        // Build single master text with clear sections → then chunk
        String master = normalizeWhitespace(buildAppMasterText(app, cfg));
        Metadata md = baseAppMetadata(app);

        return chunkWithHeader(master, "App " + nz(app.getName()), md);
    }

    public String buildAppMasterText(FederatedAppDTO app, ToolConfigsDTO cfg) {
        StringBuilder sb = new StringBuilder(6_000);

        // Header
        sb.append("### App: ").append(nz(app.getName()))
                .append(" (").append(nz(app.getSlug())).append(")\n");

        // Summary block (answers: “which apps are there?”)
        if (notBlank(app.getShortDescription())) sb.append("Short: ").append(app.getShortDescription()).append('\n');
        if (notBlank(app.getLongDescription())) sb.append("Long: ").append(app.getLongDescription()).append('\n');
        if (notBlank(app.getSourceUrl())) sb.append("Source: ").append(app.getSourceUrl()).append('\n');
        if (notBlank(app.getLatestVersion())) sb.append("LatestVersion: ").append(app.getLatestVersion()).append('\n');

        String tagsLine = joinTags(app.getTags());
        if (!tagsLine.isBlank()) sb.append("Tags: ").append(tagsLine).append('\n');

        sb.append("Rating: count=").append(app.getCount()).append(", avg=").append(app.getAverage()).append('\n');

        // Config block (answers: “which parameters does the app have?”)
        if (cfg != null && !isConfigEmpty(cfg)) {
            sb.append("\n## Configuration\n");

            sb.append("\n[Hyperparameters]\n");
            if (cfg.getHyperparams() != null && !cfg.getHyperparams().isEmpty()) {
                for (ToolHyperParamConfigDTO h : cfg.getHyperparams()) {
                    if (h == null) continue;
                    sb.append("- name: ").append(nz(h.getName()));
                    if (notBlank(h.getVariableName())) sb.append(" | var: ").append(h.getVariableName());
                    if (h.getType() != null) sb.append(" | type: ").append(h.getType().name());
                    if (h.getMode() != null) sb.append(" | mode: ").append(h.getMode().name());
                    if (notBlank(h.getDefaultValue())) sb.append(" | default: ").append(h.getDefaultValue());
                    if (h.getOptions() != null && !h.getOptions().isEmpty()) {
                        sb.append(" | options: ").append(String.join(", ", h.getOptions()));
                    }
                    if (h.getMinValue() != null || h.getMaxValue() != null) {
                        sb.append(" | range: [").append(nz(String.valueOf(h.getMinValue())))
                                .append(", ").append(nz(String.valueOf(h.getMaxValue()))).append("]");
                    }
                    if (notBlank(h.getPattern())) sb.append(" | pattern: ").append(h.getPattern());
                    if (notBlank(h.getDescription()))
                        sb.append("\n  desc: ").append(h.getDescription());
                    sb.append('\n');
                }
            } else sb.append("(none)\n");

            sb.append("\n[Inputs]\n");
            if (cfg.getInput() != null && !cfg.getInput().isEmpty()) {
                for (ToolInputConfigDTO i : cfg.getInput()) {
                    if (i == null) continue;
                    sb.append("- name: ").append(nz(i.getName()));
                    if (notBlank(i.getVariableName())) sb.append(" | var: ").append(i.getVariableName());
                    if (i.getType() != null) sb.append(" | type: ").append(i.getType().name());
                    if (i.getMode() != null) sb.append(" | mode: ").append(i.getMode().name());
                    sb.append(" | required: ").append(i.isRequired());
                    if (notBlank(i.getDelimiter())) sb.append(" | delimiter: ").append(i.getDelimiter());
                    if (i.getHasHeader() != null && i.getHasHeader()) sb.append(" | hasHeader: true");
                    if (notBlank(i.getMinValue()) || notBlank(i.getMaxValue())) {
                        sb.append(" | valueRange: [").append(nz(i.getMinValue())).append(", ").append(nz(i.getMaxValue())).append("]");
                    }
                    if (notBlank(i.getShape())) sb.append(" | shape: ").append(i.getShape());
                    if (notBlank(i.getDescription()))
                        sb.append("\n  desc: ").append(i.getDescription());
                    sb.append('\n');
                }
            } else sb.append("(none)\n");

            sb.append("\n[Outputs]\n");
            if (cfg.getOutput() != null && !cfg.getOutput().isEmpty()) {
                for (ToolOutputConfigDTO o : cfg.getOutput()) {
                    if (o == null) continue;
                    sb.append("- name: ").append(nz(o.getName()));
                    if (notBlank(o.getVariableName())) sb.append(" | var: ").append(o.getVariableName());
                    if (o.getType() != null) sb.append(" | type: ").append(o.getType().name());
                    if (o.getMode() != null) sb.append(" | mode: ").append(o.getMode().name());
                    if (o.getHasHeader() != null && o.getHasHeader())
                        sb.append(" | hasHeader: ").append(o.getHasHeader());
                    if (notBlank(o.getDelimiter())) sb.append(" | delimiter: ").append(o.getDelimiter());
                    if (notBlank(o.getMinValue()) || notBlank(o.getMaxValue())) {
                        sb.append(" | valueRange: [").append(nz(o.getMinValue())).append(", ").append(nz(o.getMaxValue())).append("]");
                    }
                    if (notBlank(o.getShape())) sb.append(" | shape: ").append(o.getShape());
                    if (notBlank(o.getDescription()))
                        sb.append("\n  desc: ").append(o.getDescription());
                    sb.append('\n');
                }
            } else sb.append("(none)\n");
        }

        return sb.toString();
    }

    private Metadata baseAppMetadata(FederatedAppDTO app) {
        Metadata md = new Metadata();
        md.put(MetaKeys.KIND, "app_summary");
        if (app.getId() != null) md.put(MetaKeys.APP_ID, app.getId().toString());
        md.put(MetaKeys.APP_NAME, nz(app.getName()));
        md.put(MetaKeys.APP_VERSION_ID, nz(app.getLatestVersion()));
        if (app.getType() != null) md.put(MetaKeys.APP_TYPE, app.getType().name());
        return md;
    }

    /* ---------------------------- MODEL ----------------------------- */

    public List<Document> buildModelDocs(ModelDTO model, FederatedAppDTO appOrNull) {
        String master = normalizeWhitespace(buildModelMasterText(model, appOrNull));

        Metadata md = new Metadata();
        md.put(MetaKeys.KIND, "model_summary");
        if (model.getId() != null) md.put(MetaKeys.MODEL_ID, model.getId().toString());
        md.put(MetaKeys.MODEL_NAME, nz(model.getName()));
        if (model.getPublishStatus() != null) md.put(MetaKeys.MODEL_PUBLISH_STATUS, model.getPublishStatus().name());
        if (model.getLastVersion() != null && notBlank(model.getLastVersion().getModelVersion())) {
            // Keep consistent: model metadata uses the "modelVersion" field content
            md.put(MetaKeys.MODEL_VERSION, model.getLastVersion().getModelVersion());
        }
        if (appOrNull != null) {
            if (appOrNull.getId() != null) md.put(MetaKeys.APP_ID, appOrNull.getId().toString());
            md.put(MetaKeys.APP_NAME, nz(appOrNull.getName()));
        }

        return chunkWithHeader(master, "Model " + nz(model.getName()), md);
    }

    private String buildModelMasterText(ModelDTO model, FederatedAppDTO appOrNull) {
        StringBuilder sb = new StringBuilder(1_500);
        sb.append("### Model: ").append(nz(model.getName())).append('\n');
        if (notBlank(model.getShortDescription()))
            sb.append("Short: ").append(model.getShortDescription()).append('\n');
        if (notBlank(model.getLongDescription())) sb.append("Long: ").append(model.getLongDescription()).append('\n');
        if (model.getLastVersion() != null && notBlank(model.getLastVersion().getModelVersion())) {
            sb.append("LastVersion: ").append(model.getLastVersion().getModelVersion()).append('\n');
        }
        if (appOrNull != null) {
            sb.append("RelatedApp: ").append(nz(appOrNull.getName()));
            if (notBlank(appOrNull.getSlug())) sb.append(" (").append(appOrNull.getSlug()).append(')');
            sb.append('\n');
        }
        return sb.toString();
    }

    /* --------------------------- UTILITIES -------------------------- */

    private List<Document> chunkWithHeader(String master, String headerTitle, Metadata md) {
        List<String> chunks = chunkText(master, CHUNK_SIZE, CHUNK_OVERLAP);
        int total = chunks.size();
        List<Document> docs = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            String header = "### " + headerTitle + " — chunk " + (i + 1) + "/" + total + "\n";
            docs.add(Document.from(header + chunks.get(i), md));
        }
        return docs;
    }

    private static List<String> chunkText(String text, int size, int overlap) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        int len = text.length();
        int start = 0;
        int effOverlap = Math.max(0, Math.min(overlap, size / 2));

        while (start < len) {
            int end = Math.min(len, start + size);
            // Try to end on a boundary for readability
            int bestEnd = end;
            for (int i = end; i > start + size / 2 && i > start; i--) {
                char c = text.charAt(i - 1);
                if (c == '\n' || c == '.' || c == ';') {
                    bestEnd = i;
                    break;
                }
            }
            String chunk = text.substring(start, bestEnd).trim();
            if (!chunk.isEmpty()) out.add(chunk);
            if (bestEnd >= len) break;
            start = bestEnd - effOverlap; // overlap
            if (start < 0) start = 0;
        }
        return out;
    }

    private static String normalizeWhitespace(String s) {
        if (s == null) return "";
        // collapse excessive blank lines/spaces to keep chunks dense and clean
        return s.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?m)^\\s+", "")
                .replaceAll("(?m)\\s+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String joinTags(Set<FederatedAppTagDTO> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return tags.stream()
                .map(t -> t == null ? null : t.getName())
                .filter(StoreIngestor::notBlank)
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean isConfigEmpty(ToolConfigsDTO cfg) {
        return (cfg.getHyperparams() == null || cfg.getHyperparams().isEmpty())
                && (cfg.getInput() == null || cfg.getInput().isEmpty())
                && (cfg.getOutput() == null || cfg.getOutput().isEmpty());
    }
}
