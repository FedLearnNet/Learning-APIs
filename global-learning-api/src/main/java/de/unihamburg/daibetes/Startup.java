package de.unihamburg.daibetes;

import de.unihamburg.daibetes.agent.embedding.EmbeddingAO;
import de.unihamburg.daibetes.agent.store.StoreIngestor;
import de.unihamburg.daibetes.api.app.ToolExternalHandler;
import de.unihamburg.daibetes.api.app.ToolImportBuildBO;
import de.unihamburg.daibetes.api.workflow.export.WorkflowExportBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class Startup {

    @Inject
    StoreIngestor storeIngestor;

    @Inject
    ToolExternalHandler toolExternalHandler;

    @Inject
    ToolImportBuildBO toolImportBuildBO;

    @Inject
    WorkflowExportBO workflowExportBO;

    @Inject
    FLNetConfig config;

    @Inject
    EmbeddingAO embeddingAO;

    public void onStartup(@Observes StartupEvent event) {
        try {
            Log.infof("Starting Quarkus");

            if (config.createVectorExtensionOnStartup()) {
                Log.infof("Ensuring vector extension exists");
                if (embeddingAO.prepareVectorExtensionTransactional()) {
                    Log.infof("Vector extension is ready");
                } else {
                    Log.errorf("Failed to create vector extension");
                }
            } else {
                Log.infof("Skipping vector extension creation on startup");
            }
            if (storeIngestor.initEmbeddingStore()) {
                Log.infof("Embedding store is ready");
            } else {
                Log.errorf("Failed to initialize embedding store");
            }
            if (config.toolImports().enabled()) {
                Log.infof("Starting tool imports");
                Set<UUID> importedAppIds = toolExternalHandler.importAllToolsTransactional();
                // the import is committed here, which the pipeline needs to read the new versions
                if (config.toolImports().buildOnImport()) {
                    toolImportBuildBO.buildMissingImagesAsync(importedAppIds);
                } else {
                    Log.infof("Builds of imported tools have been disabled");
                }
            } else {
                Log.infof("Tool imports have been disabled");
            }
            if (config.workflowImports().enabled()) {
                Log.infof("Starting workflow imports");
                workflowExportBO.importWorkflows();
            } else {
                Log.infof("Workflow imports have been disabled");
            }
            if (config.ingestor().enableStartUp()) {
                Log.infof("Starting store ingestor");
                storeIngestor.ingest();
            } else {
                Log.infof("Ingestor has been disabled");
            }
        } catch (Exception e) {
            Log.errorf("\n====================\nError during startup:\n%s\n====================", e.getMessage(), e);

        }
    }
}
