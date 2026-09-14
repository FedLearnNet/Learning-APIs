package de.unihamburg.daibetes.api.app.config;


import bio.cosy.feddb.core.api.app.FederatedAppType;

import java.util.EnumMap;
import java.util.Map;

/**
 * This class defines the configuration policy for different tool types in the federated learning application.
 * It specifies whether a tool type needs input/output configuration, whether it supports training, and other related settings.
 * <p>
 * Frontend: projects/global-app/src/app/modules/tool-development/model/tool-config-type.ts
 * Backend: global-learning-api/src/main/java/de/unihamburg/daibetes/api/app/config/ToolTypeConfigPolicy.java
 * Python: pyfedappwrap/engine/config/config-policy.py
 */
public final class ToolTypeConfigPolicy {

    public static final ToolTypeNeedsConfig DEFAULT_OPTIONS = ToolTypeNeedsConfig.defaults();

    public static final Map<FederatedAppType, ToolTypeNeedsConfig> CONFIG_MAP;

    static {
        EnumMap<FederatedAppType, ToolTypeNeedsConfig> m = new EnumMap<>(FederatedAppType.class);

        m.put(FederatedAppType.PRE_PROCESSING, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(true)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(false).canEditOnlySchema(false)
                .build());

        m.put(FederatedAppType.ANALYSIS, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(true)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(true).canEditOnlySchema(false)
                .build());

        m.put(FederatedAppType.SELF_LEARNED, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(true)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(false).canEditOnlySchema(false)
                .build());

        m.put(FederatedAppType.DATA_TRANSFORMATION, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(false)
                .needsOutputConfig(true).canEditOutputConfig(false)
                .supportsTraining(false).canEditOnlySchema(true)
                .build());

        m.put(FederatedAppType.EXTRACTOR, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(true)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(false).canEditOnlySchema(true)
                .build());

        m.put(FederatedAppType.POST_PROCESSING, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(true)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(false).canEditOnlySchema(false)
                .build());

        m.put(FederatedAppType.EVALUATION, ToolTypeNeedsConfig.builder()
                .needsInputConfig(true).canEditInputConfig(true)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(false).canEditOnlySchema(false)
                .build());

        m.put(FederatedAppType.EXPORT, ToolTypeNeedsConfig.builder()
                .needsInputConfig(false).canEditInputConfig(false)
                .needsOutputConfig(true).canEditOutputConfig(true)
                .supportsTraining(false).canEditOnlySchema(false)
                .build());

        CONFIG_MAP = Map.copyOf(m); // immutable
    }

    public static void checkNeedsInput(FederatedAppType type) {
        ToolTypeNeedsConfig cfg = forType(type);

        if (!cfg.isNeedsInputConfig()) {
            throw new IllegalStateException(
                    "Input configuration is not supported for tool type " + type);
        }
    }

    public static void checkNeedsOutput(FederatedAppType type) {
        ToolTypeNeedsConfig cfg = forType(type);

        if (!cfg.isNeedsOutputConfig()) {
            throw new IllegalStateException(
                    "Input configuration is not supported for tool type " + type);
        }
    }

    public static void checkInputConfigUpdateAllowed(FederatedAppType type) {
        ToolTypeNeedsConfig cfg = forType(type);

        if (!cfg.isCanEditInputConfig()) {
            throw new IllegalStateException(
                    "Input configuration cannot be edited for tool type " + type);
        }
    }

    public static void checkOutputConfigUpdateAllowed(FederatedAppType type) {
        ToolTypeNeedsConfig cfg = forType(type);

        if (!cfg.isCanEditOutputConfig()) {
            throw new IllegalStateException(
                    "Output configuration cannot be edited for tool type " + type);
        }
    }

    public static boolean canOnlyEditSchema(FederatedAppType type) {
        ToolTypeNeedsConfig cfg = forType(type);
        return cfg.isCanEditOnlySchema();
    }

    public static void checkTrainingUpdateAllowed(FederatedAppType type) {
        ToolTypeNeedsConfig cfg = forType(type);

        if (!cfg.isSupportsTraining()) {
            throw new IllegalStateException(
                    "Training is not supported for tool type " + type);
        }
    }


    public static ToolTypeNeedsConfig forType(FederatedAppType type) {
        if (type == null) return DEFAULT_OPTIONS;
        return CONFIG_MAP.getOrDefault(type, DEFAULT_OPTIONS);
    }
}
