package de.unihamburg.daibetes;

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
    UmlsStartupHandler umlsStartupHandler;

    @Inject
    AutoImportHandler autoImportHandler;

    void onStartup(@Observes @Priority(Integer.MAX_VALUE) StartupEvent event) {
        umlsStartupHandler.importOnStartup()
                .onFailure().recoverWithNull()
                .chain(autoImportHandler::importAll)
                .subscribe().with(
                        ignored -> Log.info("Datamodeler startup imports completed"),
                        error -> Log.errorf(error, "Datamodeler startup imports failed: %s", error.getMessage())
                );
    }
}
