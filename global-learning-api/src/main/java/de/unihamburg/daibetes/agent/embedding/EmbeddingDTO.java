package de.unihamburg.daibetes.agent.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class EmbeddingDTO {
    private UUID embeddingId;
    private String embedding;
    private String text;
    private Object meta;
}
