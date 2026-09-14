package de.unihamburg.daibetes.api.umls.importer;

import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.ontology.OntologyRAG;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @ConfigProperty(name = "umls.auto-import.enabled", defaultValue = "false")
    boolean autoImportEnabled;

    @ConfigProperty(name = "umls.import.mrconso")
    Optional<String> mrconsoFile;

    @ConfigProperty(name = "umls.import.mrrel")
    Optional<String> mrrelFile;

    @ConfigProperty(name = "umls.auto-import.timeout-seconds", defaultValue = "3600")
    int autoImportTimeoutSeconds;

    /**
     * Observes application startup event.
     * Checks if ontology nodes exist in database.
     * If no nodes exist and auto-import is enabled, starts UMLS import
     * followed by automatic embedding — composed as a non-blocking reactive pipeline.
     * The startup method returns immediately; the pipeline runs asynchronously.
     */
    void onStartup(@Observes @Priority(Integer.MAX_VALUE) StartupEvent event) {
        if (!autoImportEnabled) {
            Log.info("UMLS auto-import disabled");
            return;
        }

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
                                return ontologyRAG.ingestAllNodes();
                            })
                            .invoke(ignored -> Log.info("UMLS embeddings completed successfully"));
                })
                .ifNoItem().after(Duration.ofSeconds(autoImportTimeoutSeconds)).fail()
                .subscribe().with(
                        ignored -> Log.info("UMLS startup pipeline completed"),
                        error -> Log.errorf("UMLS startup pipeline failed: %s", error.getMessage())
                );
    }
}
