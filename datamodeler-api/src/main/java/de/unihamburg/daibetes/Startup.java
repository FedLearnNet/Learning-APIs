package de.unihamburg.daibetes;

import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.umls.importer.UmlsStartupHandler;
import de.unihamburg.daibetes.autoimport.AutoImportHandler;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Startup {

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    UmlsStartupHandler umlsStartupHandler;

    @Inject
    AutoImportHandler autoImportHandler;

    void onStartup(@Observes @Priority(Integer.MAX_VALUE) StartupEvent event) {
        // The JSON imports don't need UMLS, so they run first and the schemas are available right
        // away. Whether UMLS is imported is decided before them, as they add ontology nodes themselves.
        ontologyDAO.hasNodes()
                .chain(hadNodes -> autoImportHandler.importAll()
                        .onFailure().invoke(error -> Log.errorf(error, "Datamodeler file imports failed: %s", error.getMessage()))
                        .onFailure().recoverWithNull()
                        .chain(() -> umlsStartupHandler.importOnStartup(!hadNodes)))
                .subscribe().with(
                        ignored -> Log.info("Datamodeler startup imports completed"),
                        error -> Log.errorf(error, "Datamodeler startup imports failed: %s", error.getMessage())
                );
    }
}
