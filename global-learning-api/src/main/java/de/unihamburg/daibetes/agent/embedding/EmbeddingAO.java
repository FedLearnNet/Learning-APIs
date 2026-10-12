package de.unihamburg.daibetes.agent.embedding;

import com.pgvector.PGvector;
import io.quarkus.arc.log.LoggerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;


@ApplicationScoped
public class EmbeddingAO {

    @Inject
    EntityManager em;

    @LoggerName("EmbeddingAO")
    Logger log;

    @Transactional
    public boolean prepareVectorExtensionTransactional() {
        try {
            em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS vector;").executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("Error while creating vector extension", e);
            return false;
        }
    }

    public boolean hasAppEmbedding(@NotNull Long appId) {
        try {
            Number count = (Number) em.createNativeQuery("SELECT COUNT(*)  FROM embeddings WHERE metadata->>'appId' = :appId")
                    .setParameter("appId", appId.toString())
                    .getSingleResult();
            return count != null && count.longValue() > 0;
        } catch (Exception e) {
            log.errorf("Error while checking if app has embedding %s", e.getMessage());
            return false;
        }
    }

    public boolean hasModelEmbedding(@NotNull Long modelId) {
        try {
            Number count = (Number) em.createNativeQuery("SELECT COUNT(*)  FROM embeddings WHERE metadata->>'modelId' = :modelId")
                    .setParameter("modelId", modelId.toString())
                    .getSingleResult();
            return count != null && count.longValue() > 0;
        } catch (Exception e) {
            log.errorf("Error while checking if model has embedding %s", e.getMessage());
            return false;
        }
    }

    public void deleteModelEmbeddings(@NotNull Long modelId) {
        try {
            em.createNativeQuery("DELETE FROM embeddings WHERE metadata->>'modelId' = :modelId")
                    .setParameter("modelId", modelId.toString())
                    .executeUpdate();
        } catch (Exception e) {
            log.error("Error while deleting paper embeddings", e);
        }
    }

    public void deleteAppEmbeddings(@NotNull Long appId) {
        try {
            em.createNativeQuery("DELETE FROM embeddings WHERE metadata->>'appId' = :appId")
                    .setParameter("appId", appId.toString())
                    .executeUpdate();
        } catch (Exception e) {
            log.error("Error while deleting paper embeddings", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<EmbeddingDTO> getAllEmbeddingsModel(@NotNull Long modelId) {
        return em.createNativeQuery("SELECT embedding_id, text, embedding, metadata FROM embeddings  WHERE metadata->>'modelId' = :modelId")
                .setParameter("modelId", modelId.toString())
                .getResultStream()
                .map(o -> {
                    Object[] row = (Object[]) o;
                    PGvector pgv = (PGvector) row[2];
                    return new EmbeddingDTO((UUID) row[0], pgv.getValue(), (String) row[1], row[3]);
                }).toList();
    }

    @SuppressWarnings("unchecked")
    public List<EmbeddingDTO> getAllEmbeddingsApp(@NotNull Long appId) {
        return em.createNativeQuery("SELECT embedding_id, text, embedding, metadata FROM embeddings  WHERE metadata->>'appId' = :appId")
                .setParameter("appId", appId.toString())
                .getResultStream()
                .map(o -> {
                    Object[] row = (Object[]) o;
                    PGvector pgv = (PGvector) row[2];
                    return new EmbeddingDTO((UUID) row[0], pgv.getValue(), (String) row[1], row[3]);
                }).toList();
    }
}
