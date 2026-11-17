package bio.cosy.feddb.local.agent.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SapBERTEmbeddingModel {

    @ConfigProperty(name = "embedding.model.path-to-model")
    Optional<String> pathToModel;

    @ConfigProperty(name = "embedding.model.path-to-tokenizer")
    Optional<String> pathToTokenizer;

    private EmbeddingModel embeddingModel;
    private boolean initialized;

    public boolean enabled() {
        return model() != null;
    }

    public List<Embedding> embedAll(List<String> texts) {
        return model().embedAll(texts.stream().map(TextSegment::from).toList()).content();
    }

    private synchronized EmbeddingModel model() {
        if (!initialized) {
            initialized = true;
            embeddingModel = load();
        }
        return embeddingModel;
    }

    private EmbeddingModel load() {
        Optional<String> model = pathToModel.flatMap(this::resolvePath);
        Optional<String> tokenizer = pathToTokenizer.flatMap(this::resolvePath);
        if (model.isEmpty() || tokenizer.isEmpty()) {
            Log.infof("SapBERT not found (model %s, tokenizer %s), column matching runs without embeddings",
                    pathToModel.orElse("<unset>"), pathToTokenizer.orElse("<unset>"));
            return null;
        }
        try {
            EmbeddingModel loaded = new OnnxEmbeddingModel(model.get(), tokenizer.get(), PoolingMode.CLS);
            Log.infof("Initialized SapBERT embedding model from %s", model.get());
            return loaded;
        } catch (RuntimeException e) {
            Log.warn("Could not load the SapBERT embedding model, column matching runs without embeddings", e);
            return null;
        }
    }

    private Optional<String> resolvePath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        for (String candidate : List.of(path, "src/main/resources/" + path)) {
            if (new File(candidate).isFile()) {
                return Optional.of(candidate);
            }
        }
        return Optional.ofNullable(Thread.currentThread().getContextClassLoader().getResource(path)).map(URL::getPath);
    }
}
