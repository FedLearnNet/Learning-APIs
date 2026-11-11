package de.unihamburg.daibetes.api.umls.importer;

import de.unihamburg.daibetes.api.feature.FeatureAvailableBO;
import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.ontology.OntologyRAG;
import de.unihamburg.daibetes.config.UMLSConfig;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class UmlsStartupHandler {

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    UmlsImportBO umlsImportBO;

    @Inject
    OntologyRAG ontologyRAG;

    @Inject
    FeatureAvailableBO featureAvailableBO;

    @Inject
    UMLSConfig umlsConfig;


    /**
     * Checks if ontology nodes exist in database.
     * If no nodes exist and auto-import is enabled, starts UMLS import
     * followed by automatic embedding — composed as a non-blocking reactive pipeline.
     * Started by {@link de.unihamburg.daibetes.Startup}.
     *
     * @param force skip the check for existing ontology nodes, for callers that checked the
     *              database themselves (Startup does so before the JSON imports add nodes)
     */
    public Uni<Void> importOnStartup(boolean force) {
        if (!umlsConfig.autoImport().enabled()) {
            Log.info("UMLS auto-import disabled");
            return Uni.createFrom().voidItem();
        }

        Optional<String> mrconsoFile = umlsConfig.importConfig().mrconso();
        Optional<String> mrrelFile = umlsConfig.importConfig().mrrel();

        if (mrconsoFile.isEmpty() || mrconsoFile.get().isEmpty()) {
            Log.warn("UMLS auto-import enabled but mrconso file not configured (umls.import.mrconso)");
            return Uni.createFrom().voidItem();
        }

        if (mrrelFile.isEmpty() || mrrelFile.get().isEmpty()) {
            Log.warn("UMLS auto-import enabled but mrrel file not configured (umls.import.mrrel)");
            return Uni.createFrom().voidItem();
        }

        String mrconso = mrconsoFile.get().trim();
        String mrrel = mrrelFile.get().trim();

        Log.info("Scheduling UMLS startup pipeline...");

        Uni<Boolean> hasNodesCheck = force ? Uni.createFrom().item(false) : ontologyDAO.hasNodes();
        return hasNodesCheck
                .chain(hasNodes -> {
                    if (hasNodes) {
                        Log.info("Ontology nodes found in database, skipping auto-import");
                        return Uni.createFrom().<Void>nullItem();
                    }
                    Log.infof("Starting UMLS import with files: %s, %s", mrconso, mrrel);
                    return umlsImportBO.importUmls(mrconso, mrrel)
                            .invoke(summary -> Log.infof("UMLS import completed: %s", summary))
                            .chain(ignored -> {
                                Log.info("UMLS import finished, triggering automatic embeddings...");
                                if (!featureAvailableBO.isEmbeddingEnabled()) {
                                    Log.warn("Embedding is not enabled, skipping automatic embedding after UMLS import");
                                    return Uni.createFrom().<Void>nullItem();
                                }
                                return ontologyRAG.ingestAllNodes();
                            })
                            .invoke(ignored -> Log.info("UMLS embeddings completed successfully"));
                })
                .ifNoItem().after(Duration.ofSeconds(umlsConfig.autoImport().timeoutSeconds())).fail()
                .invoke(ignored -> Log.info("UMLS startup pipeline completed"))
                .onFailure().invoke(error -> Log.errorf("UMLS startup pipeline failed: %s", error.getMessage()))
                .replaceWithVoid();
    }
}
