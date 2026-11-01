package de.unihamburg.daibetes.config;

import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;
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

    // Tool, workflow and store imports run after startup instead of delaying it
    @WithName("startup.background-imports")
    @WithDefault("true")
    boolean backgroundStartupImports();

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

    @WithName("test-engine")
    TestEngineConfig testEngine();

    interface TestEngineConfig {
        // A connected container without test runs is stopped after this time
        @WithDefault("30M")
        Duration idleTimeout();

        @WithDefault("10M")
        Duration startTimeout();

        FederatedTestConfig federated();
    }

    interface FederatedTestConfig {
        // Federated test runs use the platform's relay and controller instead of simulating them in the engine
        @WithDefault("true")
        boolean useDockerizedController();

        @WithDefault("http://relay:9140/")
        String relayUrl();

        @WithDefault("http://controller:8000/")
        String controllerOrchUrl();

        @WithDefault("http://controller:8001/")
        String controllerCommUrl();
    }

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

        // Only tools with these names or slugs are built on import, all others are imported without a build
        @WithName("build-only")
        Optional<List<String>> buildOnly();

        // Imported tool builds running at the same time
        @WithDefault("3")
        @WithName("build-parallelism")
        int buildParallelism();

        // Writes the build information of a successful import build back into the tool JSON
        @WithDefault("false")
        @WithName("replace-build-info")
        boolean replaceBuildInfo();

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
