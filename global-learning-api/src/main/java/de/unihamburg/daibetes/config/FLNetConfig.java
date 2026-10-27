package de.unihamburg.daibetes.config;

import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Optional;

//Intended for all custom properties with a "flnet" prefix
//BFF is here: bio.cosy.feddb.core.security.bff.BffConfig
@ConfigMapping(prefix = "flnet")
public interface FLNetConfig {

    GuardTrailsConfig guardtrails();

    ToolsConfig tools();

    @WithName("tool-configs")
    FLNetConfigPaths toolConfigs();

    @WithName("tool-imports")
    ToolImportConfig toolImports();

    @WithName("workflow-imports")
    ToolImportConfig workflowImports();

    @WithName("create-vector-extension-on-startup")
    @WithDefault("true")
    boolean createVectorExtensionOnStartup();

    Ingestor ingestor();

    @WithName("federated-learning")
    FederatedLearningConfig federatedLearning();

    @WithName("pipeline.builder.image")
    Optional<String> pipelineBuilderImage();

    @WithName("pipeline.rescan.cron")
    String pipelineRescanCron();

    @WithName("audit.min-acceptance-amount")
    @WithDefault("1")
    Long auditMinAcceptanceAmount();

    DataAnalysisConfig dataAnalysis();

    RuntimeConfig runtime();

    interface RuntimeConfig {
        // Shows the platform's own share of a run's time next to the app's.
        @WithName("overhead.enabled")
        @WithDefault("false")
        boolean overheadEnabled();
    }

    interface DataAnalysisConfig {

        // Timeout for the app to connect to the platform
        @WithDefault("10M")
        Duration inactivityTimeout();
    }

    interface GuardTrailsConfig {
        PromptInjectionConfig promptInjection();

        GroundingConfig grounding();
    }

    interface GroundingConfig {
        ScoreConfig score();

        @WithDefault("true")
        boolean enabled();
    }

    interface PromptInjectionConfig {
        ScoreConfig score();

        @WithDefault("true")
        boolean enabled();

        AgentConfig agent();
    }

    interface ScoreConfig {
        @WithDefault("0.7")
        double threshold();
    }

    interface AgentConfig {
        @WithDefault("true")
        boolean enabled();
    }

    interface ToolsConfig {

        SemanticScholarConfig semanticScholar();
    }

    interface SemanticScholarConfig {
        Optional<String> apiKey();

        @WithDefault("100")
        int limit();
    }


    interface ToolImportConfig extends FLNetConfigPaths {

        @WithDefault("admin")
        String owner();

        @WithDefault("false")
        boolean createUnpublishedVersion();

        @WithDefault("true")
        boolean overrideExisting();

        @WithDefault("IF_NOT_EXISTS_IN_REGISTRY")
        ToolImportBuildingOnStart buildOnImport();

        @WithDefault("true")
        @WithName("remove-image-on-build-failure")
        boolean removeImageOnBuildFailure();

    }

    interface Ingestor {

        @WithDefault("true")
        boolean enableStartUp();

        @WithDefault("false")
        boolean ignoreStartUpCheck();

    }

    interface FederatedLearningConfig {

        @WithDefault("3")
        @WithName("participants.minamount")
        int participantsMinAmount();

        @WithDefault("0")
        @WithName("participants.mindatacount")
        int participantsMinDataCount();
    }


    ObserverConfig observer();

    interface ObserverConfig {
        @WithDefault("false")
        boolean enabled();
    }
}
