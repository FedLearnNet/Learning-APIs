package de.unihamburg.daibetes.agent.workflow;

import de.unihamburg.daibetes.agent.anlysis.AgentResult;
import de.unihamburg.daibetes.agent.store.bot.StoreBot;
import de.unihamburg.daibetes.api.workflow.chat.WorkflowChatMessageDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Consumer;

@ApplicationScoped
public class WorkflowAgent {

    @Inject
    StoreBot storeBot;

    public Multi<WorkflowChatMessageDTO> answer(String request, Long workflowId) {
        WorkflowChatMessageDTO content = new WorkflowChatMessageDTO(workflowId);
        return Multi.createFrom().emitter(em -> {
            try {
                em.emit(snapshot(content, c -> c.setStatusMessage("Starting to process your request...")));

               /* String classy = requestClassifierBot.classify(request);
                RequestRoute route = RequestRoute.from(classy);
                em.emit(snapshot(content, c -> c.setStatusMessage("Classified request as: " + route)));
                Log.infof("Classified %s request as: %s", content, route);

                em.emit(snapshot(content, c -> c.setStatusMessage("Processing " + classy + " request...")));*/
                AgentResult result = answer(request);
                em.emit(snapshot(content, c -> {
                    c.setMessage(result.getMessageMarkdown());
                    c.setAction(result.getUiAction());
                    c.setDone(true);
                    c.setStatusMessage(" request processed.");
                }));

                em.complete();
            } catch (Throwable t) {
                Log.error("answer() failed", t);
                em.emit(snapshot(content, c -> c.setErrorMessage("Failed: " + t.getMessage())));
                em.complete();
            }
        });
    }

    private AgentResult answer(String content) {
        return new AgentResult(storeBot.chat(content));
    }

    private static WorkflowChatMessageDTO snapshot(
            WorkflowChatMessageDTO state,
            Consumer<WorkflowChatMessageDTO> mut
    ) {
        if (mut != null) mut.accept(state);
        return WorkflowChatMessageDTO.copy(state);
    }
}
