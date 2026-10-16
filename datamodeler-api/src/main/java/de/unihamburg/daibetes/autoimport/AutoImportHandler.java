package de.unihamburg.daibetes.autoimport;

import de.unihamburg.daibetes.api.feature.FeatureAvailableBO;
import de.unihamburg.daibetes.api.ontology.OntologyRAG;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.UUID;

@ApplicationScoped
public class AutoImportHandler {

    @Inject
    FLNetConfig flnet;

    @Inject
    OntologyAutoImporter ontologyAutoImporter;

    @Inject
    DataTypeAutoImporter dataTypeAutoImporter;

    @Inject
    SchemaAutoImporter schemaAutoImporter;

    @Inject
    OntologyRAG ontologyRAG;

    @Inject
    FeatureAvailableBO featureAvailableBO;

    public Uni<Void> importAll() {
        AutoImportContext context = new AutoImportContext();
        return ontologyAutoImporter.importAll(context)
                .chain(() -> dataTypeAutoImporter.importAll(context))
                .chain(() -> schemaAutoImporter.importAll(context))
                .chain(() -> createEmbeddings(context.getImportedOntologyIds()));
    }

    private Uni<Void> createEmbeddings(Collection<UUID> ontologyIds) {
        // loading the SapBERT model and embedding are blocking
        return Uni.createFrom().deferred(() -> ingestMissingEmbeddings(ontologyIds))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<Void> ingestMissingEmbeddings(Collection<UUID> ontologyIds) {
        if (!flnet.ontologyImports().createEmbeddings() || ontologyIds.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        if (!featureAvailableBO.isEmbeddingEnabled()) {
            Log.warn("Embedding is not enabled, skipping SapBERT embeddings for imported ontologies");
            return Uni.createFrom().voidItem();
        }
        Log.infof("Creating missing SapBERT embeddings for %d imported ontologies", ontologyIds.size());
        return ontologyRAG.ingestMissingNodes(ontologyIds)
                .invoke(() -> Log.info("SapBERT embeddings for imported ontologies completed"));
    }
}
