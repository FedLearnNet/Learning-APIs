package eval.agent.etl.harmonization.pipeline;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationAssistantBO;
import bio.cosy.feddb.local.api.harmonization.HarmonizationDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import eval.agent.etl.transform.pipeline.TransformerWriterAssistant;
import eval.agent.etl.transform.pojo.TransformerWriterSuggestion;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.APPLY;

@ApplicationScoped
public class HarmonizationFileAssistant {

    private static final long TIMEOUT_HOURS = 6;

    @Inject
    HarmonizationAssistantBO assistantBO;

    public TransformerWriterSuggestion suggest(HarmonizationDTO harmonization, Map<String, ColumnProfile> fileProfiles,
                                               String keycloakId) {
        Long id = harmonization.getId();
        List<TransformerAssistantMessageDTO> nodes = new CopyOnWriteArrayList<>();
        CompletableFuture<ConnectorDTO> applied = new CompletableFuture<>();
        assistantBO.start(harmonization, fileProfiles, keycloakId);
        Cancellable events = assistantBO.events(id).subscribe().with(message -> {
            switch (message.getType()) {
                case CHAT_MESSAGE -> answer(id, message.getChatMessage(), harmonization.getSourceKey());
                case NODE_RESULT -> nodes.add(message);
                case DONE -> assistantBO.message(id, apply(nodes));
                case APPLIED -> applied.complete(message.getConnector());
                case ERROR -> applied.completeExceptionally(new IllegalStateException(message.getMessage()));
                default -> {
                }
            }
        }, applied::completeExceptionally);
        try {
            ConnectorDTO connector = applied.orTimeout(TIMEOUT_HOURS, TimeUnit.HOURS).join();
            return new TransformerWriterSuggestion(List.copyOf(nodes), connector);
        } finally {
            events.cancel();
        }
    }

    private void answer(Long id, ModelWorkflowChatMessageDTO chat, String sourceKey) {
        if (!chat.isDone() && chat.getHumanInTheLoop() != null && !chat.getHumanInTheLoop().isEmpty()) {
            assistantBO.message(id, TransformerWriterAssistant.createAnswer(chat.getHumanInTheLoop().getFirst(), sourceKey));
        }
    }

    private static TransformerAssistantMessageDTO apply(List<TransformerAssistantMessageDTO> nodes) {
        TransformerAssistantMessageDTO apply = new TransformerAssistantMessageDTO(APPLY);
        apply.setSchemaIds(nodes.stream()
                .filter(TransformerWriterAssistant::isFound)
                .map(TransformerAssistantMessageDTO::getSchemaId)
                .toList());
        return apply;
    }
}
