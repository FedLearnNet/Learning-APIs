package de.unihamburg.daibetes.agent.anlysis.tools;

import lombok.Data;

import java.util.Map;

@Data
public class PlanNextStepDecision {
    // StoreDTO id (NOT appId/modelId); null if NONE
    private Long storeId;

    // "app" | "model" | null
    private String kind;

    // short rationale for logging
    private String reason;

    // single question if blocked, else null
    private String followUpQuestion;

    // 0..1
    private Double confidence;

    private Map<String, Object> hyperParams;
}
