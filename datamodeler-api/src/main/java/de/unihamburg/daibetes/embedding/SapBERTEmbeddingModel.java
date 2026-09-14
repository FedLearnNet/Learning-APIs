package de.unihamburg.daibetes.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.output.Response;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class SapBERTEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel embeddingModel;

    public SapBERTEmbeddingModel(
            @ConfigProperty(name = "embedding.model.path-to-model") String pathToModel,
            @ConfigProperty(name = "embedding.model.path-to-tokenizer") String pathToTokenizer) {

        String resolvedPathToModel = resolvePath(pathToModel, "Onnx Model not found: ");
        String resolvedPathToTokenizer = resolvePath(pathToTokenizer, "Onnx Tokenizer not found: ");
        Log.info("Using Onnx Embedding Model at: " + resolvedPathToModel);
        PoolingMode poolingMode = PoolingMode.CLS;
        this.embeddingModel = new OnnxEmbeddingModel(resolvedPathToModel, resolvedPathToTokenizer, poolingMode);
        Log.infof("Initialized Onnx Embedding Model with pooling mode: %s", poolingMode);
    }

    private String resolvePath(String path, String errorPrefix) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException(errorPrefix + "<empty>");
        }
        if (path.startsWith("/")) {
            return path;
        }
        return Optional.ofNullable(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(path))
                .orElseThrow(() -> new BadRequestException(errorPrefix + path))
                .getPath();
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embeddingModel.embed(textSegment);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> list) {
        return embeddingModel.embedAll(list);
    }

    @Override
    public int dimension() {
        return embeddingModel.dimension();
    }
}
