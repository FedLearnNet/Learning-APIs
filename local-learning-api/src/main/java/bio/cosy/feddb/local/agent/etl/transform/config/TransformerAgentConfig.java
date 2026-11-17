package bio.cosy.feddb.local.agent.etl.transform.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "flnet.connector.agent")
public interface TransformerAgentConfig {

    @WithDefault("false")
    boolean useStatisticPrivacy();

    @WithDefault("true")
    boolean review();

    // Settings of the evaluation (eval-dind/run_protocol.sh); the defaults are the full assistant
    @WithDefault("true")
    boolean reviewAgents();

    @WithDefault("true")
    boolean writer();

    // The best ranked column of the matcher, mapped directly, without a language model
    @WithDefault("false")
    boolean matcherOnly();

    ColumnMatcherConfig columnMatcher();

    SyntheticDataConfig syntheticData();

    interface ColumnMatcherConfig {

        @WithDefault("8")
        int candidates();

        @WithDefault("8")
        int summaryValues();

        @WithDefault("0.6")
        double textWeight();

        @WithDefault("0.6")
        double semanticBaseline();

        @WithDefault("0.85")
        double confidentText();

        @WithDefault("0.1")
        double confidentMargin();
    }

    interface SyntheticDataConfig {

        @WithDefault("false")
        boolean enabled();

        @WithDefault("20")
        int categoryLimit();
    }
}
