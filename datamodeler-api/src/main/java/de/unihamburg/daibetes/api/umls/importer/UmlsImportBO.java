package de.unihamburg.daibetes.api.umls.importer;

import de.unihamburg.daibetes.api.config.UMLSConfig;
import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.ontology.OntologyRAG;
import de.unihamburg.daibetes.api.umls.search.UMLSIdSourceDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSSearchBO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
public class UmlsImportBO {

    private volatile ImportStatus currentStatus = ImportStatus.IDLE;

    @Inject
    UmlsParser umlsParser;

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    UMLSSearchBO umlsSearchBO;

    @Inject
    ManagedExecutor executor;
    
    @Inject
    OntologyRAG ontologyRAG;

    @Inject
    UMLSConfig umlsConfig;

    /**
     * Validates configuration and fires the import process in the background.
     * Throws InternalServerErrorException if the file paths are not configured.
     */
    public void startImport(boolean createAlsoEmbedding) {
        Optional<String> mrconsoFile = umlsConfig.importConfig().mrconso();
        Optional<String> mrrelFile = umlsConfig.importConfig().mrrel();
        if (mrconsoFile.isEmpty() || mrconsoFile.get().isBlank()) {
            throw new InternalServerErrorException("UMLS mrconso file not configured (umls.import.mrconso)");
        }
        if (mrrelFile.isEmpty() || mrrelFile.get().isBlank()) {
            throw new InternalServerErrorException("UMLS mrrel file not configured (umls.import.mrrel)");
        }

        executor.execute(() ->
                importUmls(mrconsoFile.get(), mrrelFile.get())
                        .subscribe().with(
                                ok -> {
                                    Log.info("UMLS import finished");
                                    if (createAlsoEmbedding) {
                                        Log.info("Starting UMLS embedding ingestion after import");
                                        ontologyRAG.ingestAllNodes()
                                                .subscribe().with(
                                                        ignored -> Log.info("UMLS embedding ingestion finished"),
                                                        err -> Log.error("Failed to ingest all nodes", err)
                                                );
                                    }
                                },
                                err -> Log.error("UMLS import failed", err)
                        )
        );
    }


    public Uni<UmlsImportSummaryDTO> importUmls(String mrconsoPath, String mrrelPath) {
        // Check if import is already running
        if (currentStatus == ImportStatus.RUNNING) {
            return Uni.createFrom().failure(
                    new IllegalStateException("An UMLS import is already in progress. Current status: " + currentStatus)
            );
        }

        currentStatus = ImportStatus.RUNNING;

        AtomicInteger totalNodeCount = new AtomicInteger(0);
        AtomicInteger totalEdgeCount = new AtomicInteger(0);

        ConcurrentHashMap<String, AtomicInteger> nodesPerOntology = new ConcurrentHashMap<>();
        Log.infof("Importing umls from %s", mrconsoPath);
        Uni<Void> nodeImport =
                umlsParser.parseMrconsoToNodesBatched(mrconsoPath, umlsConfig.importConfig().batchSize(), umlsConfig.importConfig().sizeMax())
                        .onItem().invoke(batch ->
                                batch.forEach(node -> {
                                    totalNodeCount.incrementAndGet();
                                    node.getSabs().forEach((String ontology) ->
                                            nodesPerOntology
                                                    .computeIfAbsent(ontology, k -> new AtomicInteger())
                                                    .incrementAndGet()
                                    );

                                })
                        ).onItem().invoke(() ->
                                Log.infof("Importing nodes batch, imported: %d/%d", totalNodeCount.get(), umlsConfig.importConfig().sizeMax())
                        )
                        .onItem().transformToUniAndConcatenate(ontologyDAO::createNodeBatch)
                        .collect().last()
                        .replaceWithVoid();

        Uni<Void> edgeImport =
                umlsParser.parseMrrelToEdgesBatched(mrrelPath, umlsConfig.importConfig().batchSize(), umlsConfig.importConfig().edgeSizeMax())
                        .onItem().invoke(batch ->
                                batch.forEach(node -> {
                                    totalEdgeCount.incrementAndGet();
                                })
                        )
                        .onItem().invoke(() ->
                                Log.infof("Importing nodes edges, imported: %d/%d", totalEdgeCount.get(), umlsConfig.importConfig().edgeSizeMax())
                        )
                        .onItem().transformToUniAndConcatenate(ontologyDAO::createEdgeBatch)
                        .collect().last()
                        .replaceWithVoid();


        return nodeImport
                .chain(() -> edgeImport)
                .replaceWith(() -> {
                    Map<String, Integer> nodesPerOntologyMap = nodesPerOntology.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> e.getValue().get()
                            ));

                    UmlsImportSummaryDTO summary = new UmlsImportSummaryDTO();
                    summary.setTotalNodes(totalNodeCount.get());
                    summary.setTotalEdges(totalEdgeCount.get());
                    summary.setNodesPerOntology(nodesPerOntologyMap);
                    summary.setMaxItems(umlsConfig.importConfig().sizeMax());
                    summary.setBatchSize(umlsConfig.importConfig().batchSize());
                    return summary;
                })
                .onItem().invoke(summary -> {
                    currentStatus = ImportStatus.FINISHED;
                    Log.info("UMLS import finished successfully");
                })
                .onFailure().invoke(error -> {
                    currentStatus = ImportStatus.ERROR;
                    Log.errorf("UMLS import failed: %s", error.getMessage());
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    public ImportStatus getStatus() {
        return currentStatus;
    }

    public Uni<String> createAllParentsGraph(UMLSIdSourceDTO body) {
        return umlsSearchBO.createCytoscapeGraph(body.getId(), body.getSource())
                .onItem().transform(elements -> {
                    return "TODO";
                });
    }

}
