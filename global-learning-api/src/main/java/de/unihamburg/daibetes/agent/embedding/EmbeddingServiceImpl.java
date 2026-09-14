package de.unihamburg.daibetes.agent.embedding;

import de.unihamburg.daibetes.agent.store.bot.StoreBot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class EmbeddingServiceImpl implements EmbeddingService {
    @Inject
    EmbeddingAO ao;

    @Inject
    EmbeddingAugmentor embeddingAugmentor;

    @Inject
    StoreBot storeBot;


    @Override
    public Response hasModelEmbedding(Long modelId) {
        boolean hasEmbedding = ao.hasModelEmbedding(modelId);
        if (hasEmbedding) {
            return Response.ok().status(Response.Status.OK).build();
        } else {
            return Response.noContent().build();
        }

    }

    @Override
    public Response hasAppEmbedding(Long appId) {
        boolean hasEmbedding = ao.hasAppEmbedding(appId);
        if (hasEmbedding) {
            return Response.ok().status(Response.Status.OK).build();
        } else {
            return Response.noContent().build();
        }

    }

    @Override
    public List<EmbeddingDTO> getAllEmbeddingsForModel(Long modelId) {
        return ao.getAllEmbeddingsModel(modelId);
    }

    @Override
    public List<EmbeddingDTO> getAllEmbeddingsForApp(Long appId) {
        return ao.getAllEmbeddingsApp(appId);
    }

    @Override
    public List<EmbeddingAugmentorResultDTO> queryEmbedding(String query) {
        return embeddingAugmentor.augment(query);
    }

    @Override
    public Response answerChat(String chat) {
        String answer = storeBot.chat(chat);
        return Response.ok(answer).build();
    }
}
