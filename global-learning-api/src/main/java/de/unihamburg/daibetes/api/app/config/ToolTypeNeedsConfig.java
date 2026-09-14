package de.unihamburg.daibetes.api.app.config;


import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class ToolTypeNeedsConfig {
    boolean needsInputConfig;
    boolean canEditInputConfig;
    boolean needsOutputConfig;
    boolean canEditOutputConfig;

    // for training/prediction/both
    boolean supportsTraining;

    // for data transformation
    boolean canEditOnlySchema;

    public static ToolTypeNeedsConfig defaults() {
        return ToolTypeNeedsConfig.builder()
                .needsInputConfig(true)
                .canEditInputConfig(true)
                .needsOutputConfig(true)
                .canEditOutputConfig(true)
                .supportsTraining(false)
                .canEditOnlySchema(false)
                .build();
    }
}
