package bio.cosy.feddb.local.agent.etl.transform.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "flnet.connector.agent.column-matcher")
public interface ColumnMatcherConfig {

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
