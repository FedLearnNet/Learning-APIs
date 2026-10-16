package bio.cosy.feddb.local.agent.text;

import bio.cosy.feddb.local.agent.embedding.SapBERTEmbeddingModel;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Iterator;
import java.util.List;

@ApplicationScoped
public class SapbertSimilartity {

    @Inject
    SapBERTEmbeddingModel sapBERT;

    public double[][] semanticScoresNodeNames(List<String> columns, List<LocalSchemaNodeWithPathDTO> nodes) {
        if (sapBERT == null || !sapBERT.enabled() || columns.isEmpty()) {
            return null;
        }
        columns = columns.stream().map(c -> ColumnNames.toSearchText(c, true)).toList();

        List<String> nodeTexts = nodes.stream()
                .map(node -> {
                    String schemaName = ColumnNames.toSearchText(node.getName(), true);
                    String ontologyName = node.getOntology() == null ? null : node.getOntology().getName();
                    return ontologyName == null ? schemaName : ontologyName + " " + schemaName;
                })
                .toList();

        return semanticScores(columns, nodeTexts);
    }

    public double[][] semanticScores(List<String> columns, List<String> nodeTexts) {
        if (sapBERT == null || !sapBERT.enabled() || columns.isEmpty()) {
            return null;
        }
        try {
            List<Embedding> nodeEmbeddings = embedAll(nodeTexts);
            List<Embedding> columnEmbeddings = embedAll(columns);
            double[][] scores = new double[nodeEmbeddings.size()][columnEmbeddings.size()];
            for (int n = 0; n < nodeEmbeddings.size(); n++) {
                for (int c = 0; c < columnEmbeddings.size(); c++) {
                    scores[n][c] = nodeEmbeddings.get(n) == null || columnEmbeddings.get(c) == null ? 0
                            : CosineSimilarity.between(nodeEmbeddings.get(n), columnEmbeddings.get(c));
                }
            }
            return scores;
        } catch (RuntimeException e) {
            Log.warn("SapBERT column matching failed, continuing without embeddings", e);
            return null;
        }
    }

    // A blank text, e.g. of an unnamed column, has no meaning to compare and gets no embedding
    private List<Embedding> embedAll(List<String> texts) {
        Iterator<Embedding> embeddings = sapBERT.embedAll(texts.stream().filter(text -> text != null && !text.isBlank()).toList())
                .iterator();
        return texts.stream().map(text -> text == null || text.isBlank() ? null : embeddings.next()).toList();
    }

}
