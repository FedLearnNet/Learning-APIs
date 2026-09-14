package de.unihamburg.daibetes.api.umls.importer;

import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.umls.search.UMLSIdSourceDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSSearchBO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    @ConfigProperty(name = "umls.import.batch-size", defaultValue = "100")
    int BATCH_SIZE;

    @ConfigProperty(name = "umls.import.size-max", defaultValue = "2000")
    int DEV_MAX_ITEMS;

    @ConfigProperty(name = "umls.import.edge-size-max", defaultValue = "2000")
    int DEV_MAX_EDGE_ITEMS;

    @ConfigProperty(name = "umls.import.mrconso")
    Optional<String> mrconsoFile;

    @ConfigProperty(name = "umls.import.mrrel")
    Optional<String> mrrelFile;

    /**
     * Validates configuration and fires the import process in the background.
     * Throws InternalServerErrorException if the file paths are not configured.
     */
    public void startImport() {
        if (mrconsoFile.isEmpty() || mrconsoFile.get().isBlank()) {
            throw new InternalServerErrorException("UMLS mrconso file not configured (umls.import.mrconso)");
        }
        if (mrrelFile.isEmpty() || mrrelFile.get().isBlank()) {
            throw new InternalServerErrorException("UMLS mrrel file not configured (umls.import.mrrel)");
        }

        executor.execute(() ->
                importUmls(mrconsoFile.get(), mrrelFile.get())
                        .subscribe().with(
                                ok -> Log.info("UMLS import finished"),
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
                umlsParser.parseMrconsoToNodesBatched(mrconsoPath, BATCH_SIZE, DEV_MAX_ITEMS)
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
                                Log.infof("Importing nodes batch, imported: %d/%d", totalNodeCount.get(), DEV_MAX_ITEMS)
                        )
                        .onItem().transformToUniAndConcatenate(ontologyDAO::createNodeBatch)
                        .collect().last()
                        .replaceWithVoid();

        Uni<Void> edgeImport =
                umlsParser.parseMrrelToEdgesBatched(mrrelPath, BATCH_SIZE, DEV_MAX_EDGE_ITEMS)
                        .onItem().invoke(batch ->
                                batch.forEach(node -> {
                                    totalEdgeCount.incrementAndGet();
                                })
                        )
                        .onItem().invoke(() ->
                                Log.infof("Importing nodes edges, imported: %d/%d", totalEdgeCount.get(), DEV_MAX_EDGE_ITEMS)
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
                    summary.setMaxItems(DEV_MAX_ITEMS);
                    summary.setBatchSize(BATCH_SIZE);
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
