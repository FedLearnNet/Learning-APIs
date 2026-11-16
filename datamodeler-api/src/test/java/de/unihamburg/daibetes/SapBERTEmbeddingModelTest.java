package de.unihamburg.daibetes;

import de.unihamburg.daibetes.embedding.SapBERTEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

// deploying without SapBERT is supported, so a missing model must disable embedding instead of failing
class SapBERTEmbeddingModelTest {

    @Test
    void disabledByConfig() {
        SapBERTEmbeddingModel model = new SapBERTEmbeddingModel(false,
                Optional.of("/data/sapbert/sapbert.onnx"), Optional.of("/data/sapbert/tokenizer.json"));

        assertFalse(model.enabled());
        assertThrows(IllegalStateException.class, () -> model.embed("diabetes"));
    }

    @Test
    void disabledWhenModelFilesAreMissing() {
        SapBERTEmbeddingModel model = new SapBERTEmbeddingModel(true,
                Optional.of("/does/not/exist/sapbert.onnx"), Optional.of("/does/not/exist/tokenizer.json"));

        assertFalse(model.enabled());
    }

    @Test
    void disabledWhenPathsAreNotSet() {
        SapBERTEmbeddingModel model = new SapBERTEmbeddingModel(true, Optional.empty(), Optional.empty());

        assertFalse(model.enabled());
    }
}
