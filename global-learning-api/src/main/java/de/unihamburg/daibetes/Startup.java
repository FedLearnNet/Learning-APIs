package de.unihamburg.daibetes;

import de.unihamburg.daibetes.agent.embedding.EmbeddingAO;
import de.unihamburg.daibetes.agent.store.StoreIngestor;
import de.unihamburg.daibetes.api.app.ToolExternalHandler;
import de.unihamburg.daibetes.api.workflow.export.WorkflowExportBO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Startup {

    @Inject
    StoreIngestor storeIngestor;

    @Inject
    ToolExternalHandler toolExternalHandler;

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
            if (config.toolImports().enabled()) {
                Log.infof("Starting tool imports");
                toolExternalHandler.importAllToolsTransactional();
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
