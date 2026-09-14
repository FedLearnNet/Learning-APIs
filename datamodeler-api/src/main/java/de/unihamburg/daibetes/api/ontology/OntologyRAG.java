package de.unihamburg.daibetes.api.ontology;

import de.unihamburg.daibetes.embedding.OntologyToDocumentParser;
import de.unihamburg.daibetes.embedding.SapBERTEmbeddingModel;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

@ApplicationScoped
public class OntologyRAG {

    @Inject
    SapBERTEmbeddingModel embeddingModel;

    @Inject
    Neo4jEmbeddingStore store;

    @Inject
    OntologyDAO ontologyDAO;

    public Uni<Map<String, Double>> search(String text, int maxResults) {
        return Uni.createFrom().item(() -> searchBlocking(text, maxResults))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Map<String, Double> searchBlocking(String text, int maxResults) {
        Log.infof("Searching ontology for text: %s", text);
        List<Content> contents = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .maxResults(maxResults)
                .build()
                .retrieve(new Query(text));
        Log.infof("Retrieved %d contents from embedding store.", contents.size());
        Map<String, Double> results = contents.stream()
                .flatMap(content -> {
                    String nodeId = content.textSegment().metadata().getString("nodeId");
                    Double score = (Double) content.metadata().get(ContentMetadata.SCORE);
                    return Map.of(nodeId, score).entrySet().stream();
                })
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1
                ));
        Log.infof("Search results: %s", results);
        return results;
    }

    public Uni<Void> ingestAllNodes() {
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(500, 0))
                .build();

        return ontologyDAO.findAll()
                .onItem()
                .transform(OntologyToDocumentParser::toDocument)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .onItem().invoke(doc -> ingestor.ingest(doc))
                .onItem().invoke(doc -> Log.infof("Ingested document with metadata: %s", doc.metadata()))
                .select().last().toUni().replaceWithVoid();
    }
}
