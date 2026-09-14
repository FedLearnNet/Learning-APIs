package de.unihamburg.daibetes.api.workflow.chat;

import de.unihamburg.daibetes.agent.workflow.WorkflowAgent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class WorkflowChatBO {

    @Inject
    WorkflowAgent workflowAgent;

    public Multi<WorkflowChatMessageDTO> processMessage(String message, Long workflowId) {
        return workflowAgent.answer(message, workflowId)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
