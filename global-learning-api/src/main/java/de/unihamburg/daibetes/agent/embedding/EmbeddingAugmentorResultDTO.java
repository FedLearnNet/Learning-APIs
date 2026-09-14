package de.unihamburg.daibetes.agent.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmbeddingAugmentorResultDTO {
    String text;
    Long appId;
    Long modelId;
    Double score;
    String embeddingId;
}
