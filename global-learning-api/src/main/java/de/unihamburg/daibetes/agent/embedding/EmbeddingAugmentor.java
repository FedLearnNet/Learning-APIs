package de.unihamburg.daibetes.agent.embedding;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class EmbeddingAugmentor {


    private final EmbeddingStoreContentRetriever retriever;

    @SuppressWarnings("unchecked")
    EmbeddingAugmentor(EmbeddingStore store, EmbeddingModel model) {
        retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(model)
                .embeddingStore(store)
                .maxResults(100)
                .build();
    }

    public List<EmbeddingAugmentorResultDTO> augment(String query) {
        Metadata metadata = new Metadata(new UserMessage(query), null, null);
        Query q = new Query(query, metadata);
        List<Content> contents = retriever.retrieve(q);
        return contents.stream()
                .map(c -> {
                    String text = c.textSegment().text();
                    Double score = (Double) c.metadata().get(ContentMetadata.SCORE);
                    String embeddingId = (String) c.metadata().get(ContentMetadata.EMBEDDING_ID);
                    Long appId = c.textSegment().metadata().getString("appId") != null ?
                            Long.parseLong(c.textSegment().metadata().getString("appId")) :
                            null;
                    Long modelId = c.textSegment().metadata().getString("modelId") != null ?
                            Long.parseLong(c.textSegment().metadata().getString("modelId")) :
                            null;

                    return new EmbeddingAugmentorResultDTO(text, appId, modelId, score, embeddingId);
                })
                .toList();
    }

}
