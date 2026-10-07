package de.unihamburg.daibetes.api.umls.importer;

import de.unihamburg.daibetes.api.config.UMLSConfig;
import de.unihamburg.daibetes.api.feature.FeatureAvailableBO;
import de.unihamburg.daibetes.api.feature.FeatureAvailableMessages;
import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.ontology.OntologyRAG;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

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
     * Observes application startup event.
     * Checks if ontology nodes exist in database.
     * If no nodes exist and auto-import is enabled, starts UMLS import
     * followed by automatic embedding — composed as a non-blocking reactive pipeline.
     * The startup method returns immediately; the pipeline runs asynchronously.
     */
    void onStartup(@Observes @Priority(Integer.MAX_VALUE) StartupEvent event) {


        if (!umlsConfig.autoImport().enabled()) {
            Log.info("UMLS auto-import disabled");
            return;
        }

        Optional<String> mrconsoFile = umlsConfig.importConfig().mrconso();
        Optional<String> mrrelFile = umlsConfig.importConfig().mrrel();

        if (mrconsoFile.isEmpty() || mrconsoFile.get().isEmpty()) {
            Log.warn("UMLS auto-import enabled but mrconso file not configured (umls.import.mrconso)");
            return;
        }

        if (mrrelFile.isEmpty() || mrrelFile.get().isEmpty()) {
            Log.warn("UMLS auto-import enabled but mrrel file not configured (umls.import.mrrel)");
            return;
        }

        String mrconso = mrconsoFile.get().trim();
        String mrrel = mrrelFile.get().trim();

        Log.info("Scheduling UMLS startup pipeline...");

        ontologyDAO.hasNodes()
                .chain(hasNodes -> {
                    if (hasNodes) {
                        Log.info("Ontology nodes found in database, skipping auto-import");
                        return Uni.createFrom().<Void>nullItem();
                    }
                    Log.infof("No ontology nodes found, starting UMLS import with files: %s, %s", mrconso, mrrel);
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
                .subscribe().with(
                        ignored -> Log.info("UMLS startup pipeline completed"),
                        error -> Log.errorf("UMLS startup pipeline failed: %s", error.getMessage())
                );
    }
}
