package de.unihamburg.daibetes.agent.audit;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ToolLlmAuditEventsBO {

    @Inject
    ToolLlmAuditBO bo;

    // Audits run in memory, a restart loses them
    void onStartup(@Observes StartupEvent event) {
        int failed = bo.failInterruptedAuditsTransactional();
        if (failed > 0) {
            Log.infof("Marked %d interrupted LLM audits as failed", failed);
        }
    }
}
