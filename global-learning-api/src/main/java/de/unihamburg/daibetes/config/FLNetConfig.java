package de.unihamburg.daibetes.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

//@StaticInitSafe
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

    GitLabConfig gitlab();

    @WithName("federated-learning")
    FederatedLearningConfig federatedLearning();

    @WithName("feddb-client")
    FedDBClientConfig feddbClient();

    @WithName("pipeline.builder.image")
    Optional<String> pipelineBuilderImage();

    @WithName("audit.min-acceptance-amount")
    @WithDefault("1")
    Long auditMinAcceptanceAmount();

    DataAnalysisConfig dataAnalysis();

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

    interface FLNetConfigPaths {

        @WithDefault("false")
        boolean enabled();

        Optional<List<Path>> paths();
    }

    interface ToolImportConfig extends FLNetConfigPaths {

        @WithDefault("false")
        boolean createUnpublishedVersion();

        @WithDefault("true")
        boolean overrideExisting();
    }

    interface Ingestor {

        @WithDefault("true")
        boolean enableStartUp();

        @WithDefault("false")
        boolean ignoreStartUpCheck();

    }

    interface GitLabConfig {
        @WithName("issue.token")
        String issueToken();

        @WithName("issue.project-id")
        @WithDefault("9")
        int issueProjectId();
    }

    interface FederatedLearningConfig {

        @WithDefault("3")
        @WithName("participants.minamount")
        int participantsMinAmount();

        @WithDefault("0")
        @WithName("participants.mindatacount")
        int participantsMinDataCount();
    }

    interface FedDBClientConfig {

        AuthConfig auth();
    }

    interface AuthConfig {

        @WithName("enable")
        @WithDefault("true")
        boolean enabled();
    }

    ObserverConfig observer();

    interface ObserverConfig {
        @WithDefault("false")
        boolean enabled();
    }}
