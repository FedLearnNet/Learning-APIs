package de.unihamburg.daibetes.embedding;

import de.unihamburg.daibetes.api.feature.FeatureAvailableMessages;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.output.Response;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class SapBERTEmbeddingModel implements EmbeddingModel {

    // null when embedding is disabled or the model could not be loaded, see enabled()
    private final EmbeddingModel embeddingModel;

    public SapBERTEmbeddingModel(
            @ConfigProperty(name = "embedding.enabled", defaultValue = "true") boolean enabled,
            @ConfigProperty(name = "embedding.model.path-to-model") Optional<String> pathToModel,
            @ConfigProperty(name = "embedding.model.path-to-tokenizer") Optional<String> pathToTokenizer) {
        this.embeddingModel = enabled ? load(pathToModel, pathToTokenizer) : null;
        if (!enabled) {
            Log.info("SapBERT embedding disabled via embedding.enabled=false");
        }
    }

    private static EmbeddingModel load(Optional<String> pathToModel, Optional<String> pathToTokenizer) {
        try {
            String resolvedPathToModel = resolvePath(pathToModel, "Onnx Model not found: ");
            String resolvedPathToTokenizer = resolvePath(pathToTokenizer, "Onnx Tokenizer not found: ");
            Log.info("Using Onnx Embedding Model at: " + resolvedPathToModel);
            PoolingMode poolingMode = PoolingMode.CLS;
            EmbeddingModel model = new OnnxEmbeddingModel(resolvedPathToModel, resolvedPathToTokenizer, poolingMode);
            Log.infof("Initialized Onnx Embedding Model with pooling mode: %s", poolingMode);
            return model;
        } catch (Exception e) {
            // deploying without SapBERT is supported, the embedding features are then turned off
            Log.errorf("SapBERT model not available, embedding disabled: %s", e.getMessage());
            return null;
        }
    }

    private static String resolvePath(Optional<String> path, String errorPrefix) {
        String value = path.filter(p -> !p.isBlank())
                .orElseThrow(() -> new IllegalStateException(errorPrefix + "<empty>"));
        if (value.startsWith("/")) {
            if (!Files.isRegularFile(Path.of(value))) {
                throw new IllegalStateException(errorPrefix + value);
            }
            return value;
        }
        return Optional.ofNullable(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(value))
                .orElseThrow(() -> new IllegalStateException(errorPrefix + value))
                .getPath();
    }

    private EmbeddingModel model() {
        if (embeddingModel == null) {
            throw new IllegalStateException(FeatureAvailableMessages.EMBEDDING_DISABLED);
        }
        return embeddingModel;
    }

    @Override
    public Response<Embedding> embed(String text) {
        return model().embed(text);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return model().embed(textSegment);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> list) {
        return model().embedAll(list);
    }

    @Override
    public int dimension() {
        return model().dimension();
    }

    public boolean enabled() {
        return embeddingModel != null;
    }
}
